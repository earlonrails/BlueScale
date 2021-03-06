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

package org.bluescale.server

import javax.servlet.ServletException
import javax.servlet.http.HttpServlet
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse
import java.io._
import org.mortbay.jetty.servlet.ServletHandler 
import org.mortbay.jetty.servlet.ServletHandler
import org.mortbay.jetty.handler.ContextHandlerCollection
import org.mortbay.jetty.Server
import org.mortbay.jetty.servlet.Context
import org.mortbay.jetty.servlet.ServletHolder
import org.mortbay.jetty.bio.SocketConnector
import org.mortbay.jetty.handler.AbstractHandler
import org.bluescale.telco.api._
import org.bluescale.telco.jainsip.SipTelcoServer
import java.net._
import scala.xml._
import org.bluescale.blueml._
import scala.collection.JavaConversions._ 

class WebServer(apiPort:Int,
                adminPort:Int,
                telcoServer:TelcoServer,
                callbackUrl:String) {
    
    private val wserver = new Server()
    val engine = new Engine(telcoServer, callbackUrl)    
    telcoServer.setIncomingCallback( conn => engine.handleIncomingCall(callbackUrl,conn) )
    telcoServer.setUnjoinCallback( (unjoiner, conn) => engine.handleUnjoin(callbackUrl, unjoiner, conn) )
    telcoServer.setRegisterCallback((registerInfo) => engine.handleRegisterRequest(callbackUrl,registerInfo))
    //we don't care about the disconnectCallbacks as muchas  conversation callbacks:w
    //telcoServer.setDisconnectedCallback( conn => engine.handleDisconnect(callbackUrl, conn) )
    initWebServer()

    def initWebServer() {
        val apiConnector = new SocketConnector()
        apiConnector.setPort(apiPort)
        wserver.setConnectors( List(apiConnector).toArray )
        val context = new Context(wserver, "/", Context.SESSIONS)
        context.addServlet( new ServletHolder( new CallServlet(telcoServer,engine) ), "/*" )
   }

    def start() =
        wserver.start()
    
    def stop() =
        wserver.stop()
}


class CallServlet(telcoServer:TelcoServer,
                  engine:Engine) extends HttpServlet {
    
    override def doGet(request:HttpServletRequest, response:HttpServletResponse) = {
        val arr = request.getPathInfo().split("/")
        println("Path = " + request.getPathInfo() )
        printParams(request)
        var status = HttpServletResponse.SC_OK
        try {
            if (arr(1).equals("Calls") && arr.length == 2) {
               engine.newCall(  request.getParameter("To"),
                                request.getParameter("From"), 
                                request.getParameter("Url"))
            } else if (arr(1).equals("Calls") && arr.length > 3) {
                val callid = arr(2)
                
                val action = arr(3) match {
                    case "Hangup" => new Hangup(request.getParameter("Url"))
                    case "Play"   => new Play(0, request.getParameter("MediaUrl"), request.getParameter("Url"))
                    case "Hold"   => new Hold() 
                    case _ => status = HttpServletResponse.SC_BAD_REQUEST
                             null
                }
                println("action = "+ action)
                engine.modifyCall(callid, action)
            }
        } catch {    
            case ex:Exception => 
                ex.printStackTrace()
                status = HttpServletResponse.SC_INTERNAL_SERVER_ERROR
        }
            
        response.setContentType("text/xml") //XML
        response.setStatus(status)
        response.getWriter().flush()
        response.getWriter().close()
    }
    

    override def doPost(request:HttpServletRequest, response:HttpServletResponse) {
        doGet(request, response)
    }

    protected def printParams(request:HttpServletRequest) =
        request.getParameterNames.foreach( name => println(" " + name + " = " + request.getParameter(name.toString()) ) )
        //{case (key, value) => println( " " + key + " = " + value.toString()) })
   
} 


