package org.tzi.use.dtdl.gui.instance;

import javax.swing.JComponent;
import org.tzi.use.dtdl.DTDLModel.Schema.Schema;
import org.tzi.use.dtdl.DTDLModel.Interface;

public interface SchemaInputAdapter {
    JComponent createInputForSchema(Schema schema, Interface iface);
    Object extractValueFromInput(JComponent comp);
}
