/* OpenRemote, the Home of the Digital Home.
* Copyright 2008-2012, OpenRemote Inc.
*
* See the contributors.txt file in the distribution for a
* full listing of individual contributors.
*
* This program is free software: you can redistribute it and/or modify
* it under the terms of the GNU Affero General Public License as
* published by the Free Software Foundation, either version 3 of the
* License, or (at your option) any later version.
*
* This program is distributed in the hope that it will be useful,
* but WITHOUT ANY WARRANTY; without even the implied warranty of
* MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
* GNU Affero General Public License for more details.
*
* You should have received a copy of the GNU Affero General Public License
* along with this program. If not, see <http://www.gnu.org/licenses/>.
*/

package org.openremote.modeler.server;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import org.openremote.modeler.client.rpc.DeviceCommandRPCService;
import org.openremote.modeler.domain.Device;
import org.openremote.modeler.domain.DeviceCommand;
import org.openremote.modeler.domain.Protocol;
import org.openremote.modeler.domain.ProtocolAttr;
import org.openremote.modeler.service.DeviceCommandService;
import org.openremote.modeler.shared.dto.DeviceCommandDTO;
import org.openremote.modeler.shared.dto.DeviceCommandDetailsDTO;

/**
 * The server side implementation of the RPC service <code>DeviceCommandRPCService</code>.
 */
public class DeviceCommandController extends BaseGWTSpringController implements
        DeviceCommandRPCService {

   /** The Constant serialVersionUID. */
   private static final long serialVersionUID = -8417889117208060088L;
   
   /** The device command service. */
   private DeviceCommandService deviceCommandService;

    /**
     * Sets the device command service.
     * 
     * @param deviceCommandRPCService the new device command service
     */
    public void setDeviceCommandService(DeviceCommandService deviceCommandRPCService) {
      this.deviceCommandService = deviceCommandRPCService;
   }

   /**
    * {@inheritDoc}
    * 
    * @see org.openremote.modeler.client.rpc.DeviceCommandRPCService#deleteCommand(long)
    */
   public Boolean deleteCommand(long id) {
      return deviceCommandService.deleteCommand(id);
   }

   @Override
   public ArrayList<DeviceCommandDTO> loadCommandsDTOByDevice(long id) {
     return deviceCommandService.loadCommandsDTOByDevice(id);
   }
   
   @Override
   public DeviceCommandDetailsDTO loadCommandDetailsDTO(long id) {
	   return deviceCommandService.loadCommandDetailsDTO(id);
   }

   @Override
   public void updateDeviceCommandWithDTO(DeviceCommandDetailsDTO dto) {
     deviceCommandService.updateDeviceCommandWithDTO(dto);
   }
   
   @Override
   public void saveNewDeviceCommand(DeviceCommandDetailsDTO dto, long deviceId) {
     deviceCommandService.saveNewDeviceCommand(dto, deviceId);
   }
}
