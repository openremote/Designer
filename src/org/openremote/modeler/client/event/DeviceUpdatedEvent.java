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
package org.openremote.modeler.client.event;

import org.openremote.modeler.shared.dto.DeviceDTO;

import com.google.gwt.event.shared.GwtEvent;

public class DeviceUpdatedEvent extends GwtEvent<DeviceUpdatedEventHandler> {

  public static Type<DeviceUpdatedEventHandler> TYPE = new Type<DeviceUpdatedEventHandler>();

  private final DeviceDTO updatedDevice;
  
  public DeviceUpdatedEvent(DeviceDTO updatedDevice) {
    super();
    this.updatedDevice = updatedDevice;
  }

  public DeviceDTO getUpdatedDevice() {
    return updatedDevice;
  }

  @Override
  public com.google.gwt.event.shared.GwtEvent.Type<DeviceUpdatedEventHandler> getAssociatedType() {
    return TYPE;
  }

  @Override
  protected void dispatch(DeviceUpdatedEventHandler handler) {
    handler.onDeviceUpdated(this);
  }

}
