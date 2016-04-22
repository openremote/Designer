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
package org.openremote.modeler.client.listener;

import com.extjs.gxt.ui.client.event.ButtonEvent;
import com.extjs.gxt.ui.client.event.SelectionListener;
import com.extjs.gxt.ui.client.widget.button.Button;
import com.extjs.gxt.ui.client.widget.form.FormPanel;

/**
 * This listener invokes form's submit event.
 * 
 * @author Dan 2009-8-21
 * 
 */
public class FormSubmitListener extends SelectionListener<ButtonEvent> {
   
   /** The form. */
   private FormPanel form;
   private Button submitBtn;
   
   /**
    * Instantiates a new form submit listener.
    * 
    * @param form
    *           the form
    */
   public FormSubmitListener(FormPanel form, Button submitBtn) {
      super();
      this.form = form;
      this.submitBtn = submitBtn;
   }



   /* (non-Javadoc)
    * @see com.extjs.gxt.ui.client.event.SelectionListener#componentSelected(com.extjs.gxt.ui.client.event.ComponentEvent)
    */
   @Override
   public void componentSelected(ButtonEvent ce) {
      if (form.isValid()) {
         submitBtn.disable();
         form.submit();
      }
   }

}
