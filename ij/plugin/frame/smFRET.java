package ij.plugin.frame;

import java.awt.Button;
import java.awt.GridLayout;
import java.awt.Panel;
import java.awt.Rectangle;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.prefs.Preferences;

import ij.IJ;
import ij.gui.Roi;

import javax.swing.JComboBox;
import javax.swing.JEditorPane;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JTextField;

public class smFRET {

	public static String donorQuadrant = "left";
	public static int radius = 10;
	public static int deltax = 256;
	public static int deltay = 0;
	private static Roi acceptor;
	private static Roi donor;
	public static void setProperties() {
		PropertyFrame.getInstance().setVisible(true);
	}
	public static class PropertyFrame extends JFrame implements ActionListener{
		private static final long serialVersionUID = 1L;
		private Panel panel;
		private JComboBox quadrantChoser;
		private JTextField jdeltaX;
		private JTextField jdeltaY;
		private JTextField jradius;
		public static PropertyFrame instance;
		public static PropertyFrame getInstance(){
			if(instance == null)
				instance = new PropertyFrame("smFRET");
			return instance;
		}
		public PropertyFrame(String title) {
			super(title);
			Preferences pref = Preferences.userNodeForPackage(PropertyFrame.class);
			if(pref.get("radius", "") != ""){
			donorQuadrant = pref.get("donorQuadrant", "");
			radius = Integer.parseInt(pref.get("radius", ""));
			deltax = Integer.parseInt(pref.get("deltax", ""));
			deltay = Integer.parseInt(pref.get("deltay", ""));
			}
			init();
			// TODO Auto-generated constructor stub
		}
		public void init(){
			this.setBounds(200, 200, 200, 300);
		    panel = new Panel();
			panel.setLayout(new GridLayout(7, 2, 1, 1));
			panel.add(new JLabel("Donor location:"));
			String[] quadrant = new String[]{"up" , "left"  , "bottom","right"};  
			quadrantChoser = new JComboBox(quadrant);  
			quadrantChoser.setSelectedItem(donorQuadrant);
			panel.add(quadrantChoser);
			panel.add(new JLabel("deltaX"));
			jdeltaX = new JTextField();
			jdeltaX.setText(Integer.toString(deltax));
			panel.add(jdeltaX);
			panel.add(new JLabel("deltaY"));
			jdeltaY = new JTextField();
			jdeltaY.setText(Integer.toString(deltay));
			panel.add(jdeltaY);
			panel.add(new JLabel("radius"));
			jradius = new JTextField();
			jradius.setText(Integer.toString(radius));
			panel.add(jradius);
			addButton("OK");
			addButton("Cancel");
			this.add(panel);
			 
		}
		void addButton(String label) {
			Button b = new Button(label);
			b.addActionListener(this);
			b.addKeyListener(IJ.getInstance());
			panel.add(b);
		}
		@Override
		public void actionPerformed(ActionEvent e) {
			if(e.getActionCommand().toString().equals("OK")){
				saveData();
				this.setVisible(false);
			}else if(e.getActionCommand().toString().equals("Cancel")){
				this.setVisible(false);
			}
		}
		private void saveData() {
			smFRET.deltax = Integer.parseInt(jdeltaX.getText());
			smFRET.deltay = Integer.parseInt(jdeltaY.getText());
			smFRET.radius = Integer.parseInt(jradius.getText());
			smFRET.donorQuadrant = (String) quadrantChoser.getSelectedItem();
			
			Preferences pref = Preferences
					.userNodeForPackage(PropertyFrame.class);
			pref.put("deltax", Integer.toString(deltax));
			pref.put("deltay", Integer.toString(deltay));
			pref.put("radius", Integer.toString(radius));
			pref.put("donorQuadrant", donorQuadrant);
		}
	}
	public static Roi[] reShapeRoi(Roi roi) {
		Rectangle rt = roi.getBounds();
		int x = rt.x+rt.width/2-radius/2;
		int y = rt.y+rt.height/2-radius/2;

		switch(donorQuadrant){
		case "up":
			if(y>255)//this point is in the bottom area(acceptor)
			{
				donor = new Roi(x-deltax,y-deltay,radius,radius);
				acceptor = new Roi(x,y,radius,radius);
			}else{
				donor = new Roi(x,y,radius,radius);
				acceptor = new Roi(x+deltax,y+deltay,radius,radius);
			}
			break;
		case "left":
			if(x>255)//this point is in the right area(acceptor)
			{
				donor = new Roi(x-deltax,y-deltay,radius,radius);
				acceptor = new Roi(x,y,radius,radius);
			}else{
				donor = new Roi(x,y,radius,radius);
				acceptor = new Roi(x+deltax,y+deltay,radius,radius);
			}
			break;
		case "bottom":
			if(y<255)//this point is in the up area (acceptor)
			{
				donor = new Roi(x+deltax,y+deltay,radius,radius);
				acceptor = new Roi(x,y,radius,radius);
			}else{
				donor = new Roi(x,y,radius,radius);
				acceptor = new Roi(x-deltax,y-deltay,radius,radius);
			}
			break;
		case "right":
			if(x<255)//this point is in the left (acceptor)
			{
				donor = new Roi(x+deltax,y+deltay,radius,radius);
				acceptor = new Roi(x,y,radius,radius);
			}else{
				donor = new Roi(x,y,radius,radius);
				acceptor = new Roi(x-deltax,y-deltay,radius,radius);
			}
			break;
		}
		return new Roi[]{donor,acceptor};
	}

}
