/*
 * USE - UML based specification environment
 * Copyright (C) 1999-2004 Mark Richters, University of Bremen
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License as
 * published by the Free Software Foundation; either version 2 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 675 Mass Ave, Cambridge, MA 02139, USA.
 */
package org.tzi.use.gui.views.diagrams.classdiagram;

import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.font.FontRenderContext;
import java.awt.geom.Line2D;
import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.List;

import org.tzi.use.gui.main.ModelBrowserSorting;
import org.tzi.use.gui.main.ModelBrowserSorting.SortChangeEvent;
import org.tzi.use.gui.main.ModelBrowserSorting.SortChangeListener;
import org.tzi.use.gui.views.diagrams.DiagramOptions;
import org.tzi.use.gui.views.diagrams.util.Util;
import org.tzi.use.uml.mm.MAttribute;
import org.tzi.use.uml.mm.MClass;
import org.tzi.use.uml.mm.MOperation;

import com.google.common.base.Predicate;
import com.google.common.collect.Collections2;

/**
 * A node representing a class.
 * 
 * @author Fabian Gutsche
 */
public class ClassNode extends ClassifierNode implements SortChangeListener {
		
    private List<MAttribute> fAttributes;
    private List<MOperation> fOperations;
    
    private final String[] fAttrValues;
    private final Color[] fAttrColors;
    
    private final String[] fOprSignatures;
    private final Color[] fOperationColors;

    // --- compact / expand controls ---
    private static final int DEFAULT_MAX_ATTR_LINES = 6;
    private static final int DEFAULT_MAX_OP_LINES  = 6;
    private static final int DEFAULT_MAX_NAME_CHARS = 30;

    private int maxAttributeLines = DEFAULT_MAX_ATTR_LINES;
    private int maxOperationLines = DEFAULT_MAX_OP_LINES;
    private int maxNameChars = DEFAULT_MAX_NAME_CHARS;

    private boolean expanded = false;
    
    private Color color = null;

    protected ClassNode( MClass cls, DiagramOptions opt ) {
    	super(cls, opt);
        
        fAttributes = cls.attributes(); 
        fAttrValues = new String[fAttributes.size()];
        fAttrColors = new Color[fAttributes.size()];

        fOperations = new ArrayList<>(Collections2.filter(cls.operations(), new Predicate<MOperation>() {
			@Override
			public boolean apply(MOperation input) {
				return !input.getAnnotationValue("View", "hideInDiagram").equals("true");
			}
		}));
        
        fOprSignatures = new String[fOperations.size()];
        fOperationColors = new Color[fOperations.size()];
                        
        copyDisplayedValues();
    }
    
    @Override
	public boolean isResizable() {
		return true;
	}
    
    /**
     * Gets the {@link MClass} represented by this class node.
     * @return The represented <code>MClass</code>
     */
    public MClass cls() {
        return (MClass)getClassifier();
    }
    
    /**
     * Gets the currently set color of the class node.
     * May be <code>null</code>.
	 * @return the color
	 */
	public Color getColor() {
		return color;
	}

	/**
	 * Sets the color of the class node to <code>color</code>.
	 * If color is <code>null</code> the default specified in the properties file color is used.
	 * @param color the color to set
	 */
	public void setColor(Color color) {
		this.color = color;
	}

	/**
	 * Sets the color of the attribute <code>attr</code> to <code>color</code>.
	 * @param att
	 * @param color
	 */
	public void setAttributeColor(MAttribute att, Color color) {
		fAttrColors[fAttributes.indexOf(att)] = color;
	}
	
	/**
	 * Sets the color of the operation <code>op</code> to <code>color</code>.
	 * @param op
	 * @param color
	 */
	public void setOperationColor(MOperation op, Color color) {
		fOperationColors[fOperations.indexOf(op)] = color;
	}
	
	/**
	 * Resets the attribute colors to the color of the class node
	 */
	public void resetAttributeColor() {
		for (int i = 0; i < fAttrColors.length; i++) {
			fAttrColors[i] = null;
		}
	}
	
	/**
	 * Resets the operation colors to the color of the class node
	 */
	public void resetOperationColor() {
		for (int i = 0; i < fOperationColors.length; i++) {
			fOperationColors[i] = null;
		}
	}
	
	/**
     * After the occurrence of an event the attribute list is updated.
     */
    @Override
	public void stateChanged( SortChangeEvent e ) {
        copyDisplayedValues();
    }
    
    private void copyDisplayedValues() {
    	fAttributes = ModelBrowserSorting.getInstance().sortAttributes( fAttributes );
    	fOperations = ModelBrowserSorting.getInstance().sortOperations( fOperations );
    	
    	for ( int i = 0; i < fAttributes.size(); i++ ) {
            MAttribute attr = fAttributes.get( i );
            fAttrValues[i] = attr.toString();
        }
    	
    	for ( int i = 0; i < fOperations.size(); i++ ) {
            MOperation opr = fOperations.get( i );
            fOprSignatures[i] = opr.signature();
        }
    }
    @Override
    protected void calculateNameRectSize(Graphics2D g, Rectangle2D.Double rect) {
        Font classNameFont;
        
        if ( cls().isAbstract() ) {
            classNameFont = g.getFont().deriveFont( Font.ITALIC );
        } else {
        	classNameFont = g.getFont();
        }

        FontMetrics classNameFontMetrics = g.getFontMetrics(classNameFont);

        String displayLabel = truncate(fLabel, maxNameChars);

        rect.width = classNameFontMetrics.stringWidth(displayLabel);
        rect.height = classNameFontMetrics.getDescent()
                + classNameFontMetrics.getAscent() + (2 * VERTICAL_INDENT);

        this.setRequiredHeight("CLASSNODE", rect.height);
        this.setRequiredWidth("CLASSNODE", rect.width + (2 * HORIZONTAL_INDENT));
    }
    
    @Override
    protected void calculateAttributeRectSize(Graphics2D g, Rectangle2D.Double rect) {
        int visible = Math.min(fAttrValues.length, maxAttributeLines);
        String[] limited = new String[visible];
        System.arraycopy(fAttrValues, 0, limited, 0, visible);
    	calculateCompartmentRectSize(g, rect, limited);
    }
    
    @Override
    protected void calculateOperationsRectSize(Graphics2D g, Rectangle2D.Double rect) {
        int visible = Math.min(fOprSignatures.length, maxOperationLines);
        String[] limited = new String[visible];
        System.arraycopy(fOprSignatures, 0, limited, 0, visible);
        calculateCompartmentRectSize(g, rect, limited);
    }
    
    public String ident() {
        return "Class." + cls().name();
    }
    public String identNodeEdge() {
        return "AssociationClass." + cls().name();
    }
    
    /**
     * Draws a box with a label.
     */
    @Override
    protected void onDraw( Graphics2D g ) {
    	Rectangle2D.Double currentBounds = getRoundedBounds();
    	
    	if (g.getClipBounds() != null && 
    	    !Util.enlargeRectangle(currentBounds, 10).intersects(g.getClipBounds())) {
    		return;
    	}
                
        int y;
                
        FontMetrics fm; 
        
        Font oldFont = g.getFont();
        if ( cls().isAbstract() ) {
            g.setFont( oldFont.deriveFont( Font.ITALIC ) );
        } 
        
        fm = g.getFontMetrics();

        String displayLabel = truncate(fLabel, maxNameChars);
        int labelWidth = fm.stringWidth(displayLabel);
        
        if ( isSelected() ) {
            g.setColor( fOpt.getNODE_SELECTED_COLOR() );
        } else {
        	if (getColor() != null)
        		g.setColor( getColor() );
        	else
        		g.setColor( fOpt.getNODE_COLOR() );
        }
        
        g.fill( currentBounds );
                        
        double x = getCenter().getX();
        x -= labelWidth / 2;
        y = (int)currentBounds.getY() + fm.getAscent() + VERTICAL_INDENT;
        g.setColor( fOpt.getNODE_LABEL_COLOR() );
        // We know that the name fits, because we require this size
        g.drawString( displayLabel, Math.round(x), y );
        
        y += VERTICAL_INDENT + fm.getDescent();
        
        // resetting font and fontMetrics if the class was abstract
        g.setFont( oldFont );
        fm = g.getFontMetrics();
        
        Line2D.Double lineAttrDivider = new Line2D.Double(currentBounds.getX(), y, currentBounds.getMaxX(), y);
        Line2D.Double lineOpDivider = new Line2D.Double(currentBounds.getX(), y, currentBounds.getMaxX(), y);
        
        if ( fOpt.isShowAttributes() ) {
            // compartment divider
        	lineAttrDivider.y1 = y;
        	lineAttrDivider.y2 = y;
        	
        	if (fAttributes.isEmpty()) {
        		y += 2 * VERTICAL_INDENT;
        	} else {
	        	Rectangle2D.Double attributeBounds = new Rectangle2D.Double(currentBounds.x, y, currentBounds.width, currentBounds.height);
	        	
	        	if (fOpt.isShowOperations()){
	        		if(!cls().operations().isEmpty()){
	        			attributeBounds.height = currentBounds.getMaxY() - y - VERTICAL_INDENT - Util.getLineHeight(fm);
	        		} else {
	        			attributeBounds.height = currentBounds.getMaxY() - y - VERTICAL_INDENT - 2*Util.getLeading(fm);
	        		}
	        	}

                int attrVisible = Math.min(fAttrValues.length, maxAttributeLines);
                String[] attrLimited = new String[attrVisible];
                Color[] attrLimitedColors = new Color[attrVisible];
                if (attrVisible > 0) {
                    System.arraycopy(fAttrValues, 0, attrLimited, 0, attrVisible);
                    if (fAttrColors != null && fAttrColors.length > 0) {
                        System.arraycopy(fAttrColors, 0, attrLimitedColors, 0, Math.min(fAttrColors.length, attrVisible));
                    }
                }
                y = drawCompartmentLimited(g, y, attrLimited, attrLimitedColors, attributeBounds, fAttributes.size());
        	}
        }
        
        if ( fOpt.isShowOperations() ) {
        	// compartment divider
        	lineOpDivider.y1 = y;
        	lineOpDivider.y2 = y;

            int opVisible = Math.min(fOprSignatures.length, maxOperationLines);
            String[] opLimited = new String[opVisible];
            Color[] opLimitedColors = new Color[opVisible];
            if (opVisible > 0) {
                System.arraycopy(fOprSignatures, 0, opLimited, 0, opVisible);
                if (fOperationColors != null && fOperationColors.length > 0) {
                    System.arraycopy(fOperationColors, 0, opLimitedColors, 0, Math.min(fOperationColors.length, opVisible));
                }
            }
            y = drawCompartmentLimited(g, y, opLimited, opLimitedColors, currentBounds, fOperations.size());
        }
        
        g.setColor( fOpt.getNODE_FRAME_COLOR() );
        g.draw( currentBounds );
        
        if ( fOpt.isShowAttributes() ) {
        	g.draw(lineAttrDivider);
        }
        if ( fOpt.isShowOperations() ) {
        	g.draw(lineOpDivider);
        }
    }

    protected int drawCompartmentLimited(Graphics2D g, int y, String[] values, Color[] colors, Rectangle2D roundedBounds, int originalTotal) {
        // create a clipped graphics so nothing draws outside the compartment
        Graphics2D g2 = (Graphics2D) g.create();
        try {
            // compute integer clip rectangle covering the roundedBounds region
            int clipX = (int) Math.floor(roundedBounds.getX());
            int clipY = (int) Math.floor(roundedBounds.getY());
            int clipW = (int) Math.ceil(roundedBounds.getWidth());
            int clipH = (int) Math.ceil(roundedBounds.getHeight());
            g2.setClip(clipX, clipY, clipW, clipH);

            FontMetrics fm = g2.getFontMetrics();
            int leading = Util.getLeading(fm);
            String shortenSuffix = "...";
            int shortensuffixLength = fm.stringWidth(shortenSuffix);

            if (values.length == 0) {
                y += 2 * leading;
                return y;
            }

            Color orgColor;
            int singleHeight = (int) Math.round(Util.getLineHeight(fm));
            Rectangle2D.Double elementRect = new Rectangle2D.Double(
                    roundedBounds.getX(), roundedBounds.getY(),
                    roundedBounds.getWidth(), roundedBounds.getHeight());

            for (int i = 0; i < values.length; ++i) {
                y += leading / 2;
                if (!isSelected() && colors != null && i < colors.length && colors[i] != null) {
                    orgColor = g2.getColor();
                    g2.setColor(colors[i]);
                    elementRect.y = y;
                    elementRect.height = singleHeight + leading;
                    g2.fill(elementRect);
                    g2.setColor(orgColor);
                }

                y += singleHeight;

                String toDraw = values[i] != null ? values[i] : "";
                double space = roundedBounds.getWidth() - (2 * HORIZONTAL_INDENT);

                // measure using g2's FontRenderContext
                double roundedRequiredSpace = Math.round(g2.getFont().getStringBounds(toDraw, g2.getFontRenderContext()).getWidth());

                if (roundedRequiredSpace > space) {
                    // shorten with suffix
                    space -= shortensuffixLength;
                    double usedSpace = 0;
                    StringBuilder newToDraw = new StringBuilder();

                    for (int index = 0; index < toDraw.length(); ++index) {
                        double charWidth = fm.charWidth(toDraw.charAt(index));
                        if (usedSpace + charWidth < space) {
                            newToDraw.append(toDraw.charAt(index));
                            usedSpace += charWidth;
                        } else {
                            break;
                        }
                    }
                    newToDraw.append(shortenSuffix);
                    toDraw = newToDraw.toString();
                }

                boolean hasMoreOriginal = originalTotal > values.length;
                boolean isLastDisplayed = (i == values.length - 1);

                if (isLastDisplayed && hasMoreOriginal) {
                    // show ellipsis centered inside compartment
                    this.drawTextCentered(g2, "...", roundedBounds.getX(), y - fm.getDescent(), roundedBounds.getWidth());
                    y += leading / 2;
                    break;
                } else {
                    g2.drawString(toDraw, Math.round(roundedBounds.getX() + HORIZONTAL_INDENT), y - fm.getDescent());
                }

                y += leading / 2;
            }
            y += leading / 2;
            return y;
        } finally {
            g2.dispose();
        }
    }

    @Override
    public boolean hasAttributes() {
    	return !fAttributes.isEmpty();
    }
    
    @Override
    public boolean hasOperations() {
    	return !fOperations.isEmpty();
    }
    
    @Override
    public String toString() {
    	return cls().name() + "(ClassNode)";
    }
    
    @Override
    protected String getStoreType() {
    	return "Class";
    }

    private String truncate(String s, int max) {
        if (s == null) return "";
        if (s.length() <= max) return s;
        if (max <= 1) return "\u2026";
        return s.substring(0, Math.max(0, max - 1)) + "\u2026";
    }
}
