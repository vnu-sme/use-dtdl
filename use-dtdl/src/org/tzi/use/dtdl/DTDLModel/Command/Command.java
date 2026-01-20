package org.tzi.use.dtdl.DTDLModel.Command;

import org.tzi.use.dtdl.DTDLModel.ContentElement;

public class Command extends ContentElement {
    private CommandPayload request;
    private CommandPayload response;

    public Command(String id) { this.id = id; this.type = "Command"; }

    public void setName(String name) { this.name = name; }

    public void setRequest(CommandPayload p) { this.request = p; }

    public void setResponse(CommandPayload p) { this.response = p; }

    public CommandPayload getRequest() { return request; }

    public CommandPayload getResponse() { return response; }

    @Override
    public void prints() { prints(0); }

    public void prints(int indent) {
        String ind = indent(indent);
        System.out.println(ind + "Command:");
        super.prints(indent + 2);
        System.out.println(ind + "  name: " + safe(name));

        if (request != null) {
            System.out.println(ind + "  request:");
            request.prints(indent + 4);
        } else {
            System.out.println(ind + "  request: (none)");
        }

        if (response != null) {
            System.out.println(ind + "  response:");
            response.prints(indent + 4);
        } else {
            System.out.println(ind + "  response: (none)");
        }
    }


}
