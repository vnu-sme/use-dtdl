package org.tzi.use.dtdl.views.nodes;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics;

public class DTDLInterfaceNode {

    private final String name;
    private final int x;
    private final int y;

    private static final int WIDTH = 160;
    private static final int HEIGHT = 80;

    public DTDLInterfaceNode(String name, int x, int y) {
        this.name = name;
        this.x = x;
        this.y = y;
    }

    public void draw(Graphics g) {
        g.setColor(Color.BLACK);
        g.drawRect(x, y, WIDTH, HEIGHT);

        g.setFont(new Font("Arial", Font.BOLD, 12));
        g.drawString("<<DTDL Interface>>", x + 10, y + 20);

        g.setFont(new Font("Arial", Font.PLAIN, 14));
        g.drawString(name, x + 10, y + 45);
    }
}
