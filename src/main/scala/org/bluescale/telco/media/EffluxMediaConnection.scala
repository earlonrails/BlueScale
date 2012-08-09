/*
*  
* This file is part of BlueScale.
*
* BlueScale is free software: you can redistribute it and/or modify
* it under the terms of the GNU Affero General Public License as published by
* the Free Software Foundation, either version 3 of the License, or
* (at your option) any later version.
* 
* BlueScale is distributed in the hope that it will be useful,
* but WITHOUT ANY WARRANTY; without even the implied warranty of
* MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
* GNU Affero General Public License for more details.
* 
* You should have received a copy of the GNU Affero General Public License
* along with BlueScale.  If not, see <http://www.gnu.org/licenses/>.
* 
* Copyright Vincent Marquez 2010
* 
* 
* Please contact us at www.BlueScale.org
*
*/

package org.bluescale.telco.media

import javax.sdp._

import org.bluescale.telco.SdpHelper;
import org.bluescale.telco.api._
import org.bluescale.telco._
import org.bluescale.util.BlueFuture
import java.io.InputStream
import com.biasedbit.efflux._
import com.biasedbit.efflux.packet.DataPacket
import com.biasedbit.efflux.participant.RtpParticipant
import com.biasedbit.efflux.participant.RtpParticipantInfo
import com.biasedbit.efflux.session._
import java.util.Timer
import java.util.TimerTask
import java.util.Date

import java.nio.ByteBuffer

class EffluxMediaConnection(telco:TelcoServer) extends MediaConnection {
  
	private val payloadType = 0 //8 for Alaw
  
    private var _joinedTo:Option[Joinable[_]] = None
    
    private var _recordedFiles = List[String]()
    private var _playedFiles   = List[String]()
    
    override def playedFiles = _playedFiles 
    
	override def recordedFiles = _recordedFiles

	private var connState = UNCONNECTED()
    
    override def joinedTo = _joinedTo
    
    val rtpport = EffluxMediaConnection.getPort()
    
    val listeningSdp = SdpHelper.createSdp(payloadType, rtpport, telco.contactIp)
    println("Our connections listening sdp = " + listeningSdp)
    
    val localparticipant = RtpParticipant.createReceiver(new RtpParticipantInfo(1), telco.listeningIp, rtpport, rtpport+1)
    println("made a media connection, listening port = " + telco.listeningIp + " rtpPort = " + rtpport)
    var effluxSession: Option[SingleParticipantSession] = None
    
    var playTimer: Option[Timer] = None
    
    var totalBytesRead = 0
    
    var totalPacketsread = 0
    
    var jitterBuffer:Option[JitterBuffer] = None
    
    private def initRtp(conn:Joinable[_]) {
    	val mediaport = SdpHelper.getMediaPort(conn.sdp) //
    	val remoteip = conn.sdp.getConnection().getAddress() //192.168.1.18
    	println("sdp = " + conn.sdp)
    	//val dtmfPayload = SdpHelper.getDtmfPayloadType(this.sdp)
    	//println("dtmfpayload = " + dtmfPayload)
    	val dtmfPayloadType = 101
    	println("conn = " + conn + " address = " + remoteip + " Mediaport = " + mediaport + " localPort = " + rtpport)
    	println(" sd = " + conn.sdp)
    	jitterBuffer = Some(new JitterBuffer(8000,160, data=> {
    		//println("  being fed audio data by the JitterBuffer!")
    		MediaFileManager.addMedia(this, data)
    	}))
    	val remote1 = RtpParticipant.createReceiver(new RtpParticipantInfo(rtpport), remoteip, mediaport, mediaport+1)
    	val session1 = new SingleParticipantSession(this.toString, payloadType, dtmfPayloadType, localparticipant, remote1, null, null)
    	effluxSession = Some(session1)
    	session1.addDataListener(getDataListener())
    	//println("STARTED THE RTP LISTENER on port" + rtpport + " remotePort =  " + mediaport + " For " + this)
   		session1.init()
    }
    
    override def join(conn:Joinable[_]) = BlueFuture(callback => {
    	//should we only do this when we get a 200 OK? should  we put it in the connect callback? 
    	//get an SDP port
    	initRtp(conn)
    	for (_ <- conn.connect(this, false)) {
    		this._joinedTo = Some(conn)
    		callback()
    	}
    })
  
    override def sdp = 
      listeningSdp
    
    def connectionState = connState

    def joinPlay(filestream:InputStream, conn:Joinable[_]) = BlueFuture( callback => { 
    	for(_ <- join(conn);
    		_ <- play(filestream))
    	  callback()
    })
    
    override def joinedMediaChange() {
    	//KILL THE OLD SESSION AND MAKE A NEW ONE.
    	effluxSession.foreach(_.terminate())
    	joinedTo.foreach( joined => initRtp(joined))
    }

    protected[telco] def unjoin() = BlueFuture(callback => {
    	Thread.sleep(1000)
    	jitterBuffer.foreach(j => j.cancel())
    	//println("total packets received = " + totalPacketsread)
    	MediaFileManager.finishAddMedia(this).foreach(newFile => _recordedFiles = newFile :: _recordedFiles)
    	println(" ~~~~~~~~~unjoin, mc = " + this.hashCode() + " files count = " + _recordedFiles.size)
    	stopPlaying()
    	unjoinCallback.foreach(_(joinedTo.get,this))
    	callback()
    })

    def makePacket(data:Array[Byte], seq:Int, delay:Int, timeoffset:Long): DataPacket = { 
       	val packet = new DataPacket()
    	packet.setPayloadType(payloadType)
       	packet.setData(data)
       	packet.setSequenceNumber(seq)
       	packet.setTimestamp(timeoffset+(seq*delay*8)) //justin karnegas figured this bug out!
       	packet
    }
    
    def play(filestream:InputStream) = BlueFuture(f => {
    	val localport = 0
    	playTimer = Some(new Timer())
    	for(joined <- joinedTo;
    		session <- effluxSession;
    		timer <- playTimer) {
    	   	val bytes = new Array[Byte](160)
    	   	val delay = 20
    	   	var seq = 1 //TODO: get a random sequence number
    	   	var read = filestream.read(bytes)//lets skip the first 160 bytes so we don't have to worry about the header for now
    	   	val now = new Date().getTime()
    	   	val timerTask = new TimerTask() {
    			def run() {
    				read match {
    				  case -1 =>
    				    	timer.cancel()
    				    	f()
    				  case _ => 
    				    	read = filestream.read(bytes)
    				    	println("sending data!")
    				    	session.sendDataPacket(makePacket(bytes, seq, delay, now))
    				    	seq += 1
    				}
    			}
    		}
    		timer.scheduleAtFixedRate(timerTask, 0, delay)
    	}
    })
    
    private def stopPlaying() = 
      playTimer.foreach( t => t.cancel())
    
    override def cancel() = BlueFuture(callback => {
    	stopPlaying()
    	callback()
    }) 
    
    override protected[telco] def connect(join:Joinable[_], connectAnyMedia:Boolean ) = BlueFuture(callback => {//doesn't need to be here? 
    	initRtp(join)
    	_joinedTo = Some(join)
    	callback()
	})
    
    override protected[telco] def connect(join:Joinable[_])= connect(join, true)
    
    private def getDataListener() = 
      	new RtpSessionDataListener() {
    		
    		var prevDtmfTime: Option[Long] = None
    		
    		def dataPacketReceived(session: RtpSession,  participant: RtpParticipantInfo, packet: DataPacket) {
    			packet.getPayloadType match {
    			  case 0 =>
    			    println("Receiving !!!!")
    			  	jitterBuffer.foreach( jb =>
    			  		jb.addToQueue(packet))   			  
    			  case _ =>
    			  		if(packet.getTimestamp() != prevDtmfTime.getOrElse(null))
    			  			dtmfEventHandler.foreach( _(DTMFEvent(packet.getDataAsArray()(0))))
    			  		prevDtmfTime = Some(packet.getTimestamp())
    			}
           	}
    	}
    
}


object EffluxMediaConnection {
	val myarray = new Array[Byte](2000)	
	val Max = 5000
	val Min = 2000
	
	def getPort(): Int = {
	  val ran = Math.random
	  val ret = Min + (ran * ((Max - Min) + 1))
	  println("returning for getPort = " + ret)
	  ret.asInstanceOf[Int]
	}
	
	def putBackPort(port:Int): Unit = {
			println("fix me")
	}
}

