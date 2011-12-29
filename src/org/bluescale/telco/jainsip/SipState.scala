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
* Please contact us at www.BlueScaleSoftware.com
*
*/
package org.bluescale.telco.jainsip

import javax.sip._
import javax.sip.header._
import javax.sdp.SessionDescription
import javax.sip.message._

case class SipState(sdp:Option[SessionDescription],
                    clientTx:Option[ClientTransaction],
                    serverTx:Option[ServerTransaction],
                    responseCode:Option[Int]) {
}

object SipState {
    def apply(sdp:SessionDescription) = new SipState(Some(sdp), None, None, None)
    def apply(sdp:SessionDescription, clientTx:ClientTransaction) = new SipState(Some(sdp), Some(clientTx), None, None)
    def apply(sdp:SessionDescription, serverTx:ServerTransaction) = new SipState(Some(sdp), None, Some(serverTx), None)
    def apply(sdp:SessionDescription, clientTx:ClientTransaction, responseCode:Int) = new SipState(Some(sdp), Some(clientTx), None, Some(responseCode))
}
