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
import org.bluescale.util.DoAsync._
import org.bluescale.telco.api._
import org.bluescale.telco._
import org.bluescale.util.BlueFuture
import java.net.DatagramSocket
import jlibrtp.RTPSession
import jlibrtp.RTPAppIntf
import jlibrtp.DataFrame
import jlibrtp.Participant
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import java.io.InputStream

class JlibMediaConnection(telco:TelcoServer) extends MediaConnection {

	println("Made JlibMediaConncetion, my hashcode = " + this.hashCode() + "!")
    private val rtpPort = JlibMediaConnection.getRtpSockets()
    
    private val rtpSession = new RTPSession(rtpPort._1, rtpPort._2)
    
    val listeningSdp = SdpHelper.createSdp(rtpPort._1.getLocalPort(), telco.contactIp)
    
    rtpSession.naivePktReception(true)
    
    rtpSession.RTPSessionRegister( new RTPAppIntf {
    		override def receiveData(frame:DataFrame, participant:Participant) =
    		  receive(frame, participant)
    			  
    		override def userEvent(etype:Int, participants:Array[Participant]) =
    		 	Unit
    			    
    		override def frameSize(payloadType:Int) = 1
    	},null, null);
    
    
    private var _joinedTo:Option[Joinable[_]] = None
    
    private var _recordedFiles = List[String]()
    private var _playedFiles   = List[String]()
    
    override def playedFiles = _playedFiles 
    
	override def recordedFiles = _recordedFiles

	private var connState = UNCONNECTED()
    
    override def joinedTo = _joinedTo
    
    override def join(conn:Joinable[_]) = BlueFuture(callback => {
    	for (_ <- conn.connect(this, false)) {
    		println(" conn " + conn + " Is now Reconnected and listening to the Media's CONNECTION INFO") 
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
        println("do nothing here?")
    }
    
    def receive(frame:DataFrame, participant:Participant)  =
      MediaFileManager.addMedia(this, frame.getConcatenatedData())
    
    
    override def play(filestream:InputStream) = BlueFuture(f => {
    	joinedTo.foreach( joined => {
    		//fixme, do we need listening ports to be in the RTPSession?
    		rtpSession.addParticipant(new Participant("",
    				SdpHelper.getMediaPort(joined.sdp), 		//RTP
    				SdpHelper.getMediaPort(joined.sdp)+1)) 	//RTCP
    			//TODO: send the packets
    			var totalSent = 0
    			val bytes = new Array[Byte](1024)
    			Thread.sleep(9000)
    			while (filestream.read(bytes) != -1) {
    				rtpSession.sendData(bytes)
    				totalSent += bytes.length
    			}
    			println("totalSent = " + totalSent)
    			rtpSession.endSession()
    			//_playedFiles = url :: _playedFiles
    			f()
    	})
    })
    
    override def cancel() = BlueFuture(callback => {
    	callback()
    })

    //PROTECTED STUFF FOR JOINABLE
    override protected[telco] def connect(join:Joinable[_])= connect(join, true)

    override protected[telco] def connect(join:Joinable[_], connectAnyMedia:Boolean ) = BlueFuture(callback => {//doesn't need to be here? 
    	
      callback()
	})
    
    //protected[telco] def onConnect(f:()=>Unit) = f() //more to do? 

    protected[telco] def unjoin() = BlueFuture(callback => {
    	finishListen()
    	println(" unjoin, mc = " + this.hashCode() + " files count = " + _recordedFiles.size)
    	callback()
    	unjoinCallback.foreach(_(joinedTo.get,this))
    })
    
    private def finishListen() =
    	MediaFileManager.finishAddMedia(this).foreach(newFile => _recordedFiles = newFile :: _recordedFiles)
    
}

object JlibMediaConnection {
	val atomicInt = new AtomicInteger(1234)	
  
	//phone nunew mber
	def mediaConnections = new ConcurrentHashMap[String, JlibMediaConnection]()
	
	def getRtpSockets() : (DatagramSocket,DatagramSocket) = {
      val i = atomicInt.getAndAdd(2)
      return (new DatagramSocket(i), new DatagramSocket(i+1))
    }
}





	
