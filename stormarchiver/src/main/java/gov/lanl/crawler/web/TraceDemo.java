package gov.lanl.crawler.web;

import java.awt.Label;
import java.awt.TextField;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import javax.swing.JApplet;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JPanel;

import gov.lanl.crawler.proto.TraceTest;

public class TraceDemo  extends JApplet{
	ClassLoader classloader;
	 private JPanel mainPanel;
	 TraceTest ts;
	public  static void main(String[] args)
	
	    {
		String[] ar = new String[1];
		ar[0]="https://www.slideshare.net/Yachiga/great-people-who-inspire";
	        JFrame frame = new JFrame("Frame");
	               frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
	              // TraceTest ts = new TraceTest();
	             //  try {
					//ts.main(ar);
				//} catch (Exception e) {
					// TODO Auto-generated catch block
				//	e.printStackTrace();
				//}
	              // frame.getContentPane().add(ts);
	               frame.pack();
	               frame.setVisible(true);
	    }
	
	  public void init() {
		   classloader = this.getClass().getClassLoader();
	        createUI();
	        add(mainPanel);
	        ts = new TraceTest();
	        
	    }

	  public void _start(String url) {
		  String[] ar = new String[1];
			//ar[0]="https://www.slideshare.net/Yachiga/great-people-who-inspire";
			ar[0]=url;
		  try {
			ts.main(ar);
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	  }
	    // create your UI here
	    private void createUI() {
	        mainPanel = new JPanel();
	        Label label1 = new Label("Enter url: ");
			TextField textField1 = new TextField("https://");

	        JButton minus = new JButton("Test");
	         minus.addActionListener(new ActionListener() {
	             public void actionPerformed(ActionEvent e) {
	            	 String temp = textField1.getText();
	            	 _start(temp);
	             }
	         });
	         mainPanel.add(label1);
	         mainPanel.add(textField1); 
	         mainPanel.add(minus);
	      
	    }
}

