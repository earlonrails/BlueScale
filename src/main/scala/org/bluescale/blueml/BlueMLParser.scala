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
package org.bluescale.blueml

import scala.xml._
import org.bluescale.telco.api._
import org.bluescale.util.Util

object BlueMLParser extends Util {

    def parse(str:String) =
       for (verb <- (XML.loadString(str) \\ "Response") \ "_" )
            yield parseVerb(verb)
    
    private def parseVerb(n:Node) : BlueMLVerb = {
        n.label match {
            case "Dial" => parseDial(n)
            case "DialVoicemail" => parseDialVoicemail(n)
            case "Say" => throw new UnsupportedOperationException("Say")
            case "Play" => parsePlay(n)
            case "Gather"=>throw new UnsupportedOperationException("Gather")
            case "Record"=>throw new UnsupportedOperationException("Record")
            case "Hangup"=>parseHangup(n)
            case "Auth" => parseAuth(n) 
            case _ => null
        }
    }

    private def parseAuth(n:Node): Auth =
    	new Auth((n \ "Password").text)
      
    private def parseDial(n:Node) : Dial = 
        new Dial( GetNonEmpty((n \ "Number").text, n.text),
                  fixNumber((n \ "From").text),
                  fixNumber((n \ "Action") .text),
                  parseInt((n \ "RingLimit").text))
    
    private def parseDialVoicemail(n:Node) : DialVoicemail = 
        new DialVoicemail( GetNonEmpty((n \ "Number").text, n.text),
                fixNumber((n \ "From").text),
                fixNumber((n \ "Action").text))
                  
    private def parsePlay(n:Node) : Play = 
        new Play( parseInt( (n \ "loop").text),
        		(n \ "MediaUrl").text,
        		(n \ "Action").text)

    private def parseInt(str:String) = 
        StrOption(str) match {
           case Some(s) => Integer.parseInt(s)
           case None => -1
        }
    
    private def parseHangup(n:Node)  = 
      new Hangup( (n \ "Action").text)

    private def fixNumber(number:String) =
        number.replace(" ", "")
        .replace("-","")
        .replace("(","")
        .replace(")","")
}


