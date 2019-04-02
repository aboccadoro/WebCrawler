package crawler;

import javax.swing.*;

public class WebCrawler extends JFrame {

    public WebCrawler() {
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(300, 300);
        setVisible(true);
        setLayout(null);
        setTitle("Web Crawler");
        var text = new JTextArea("   HTML code?");
        text.setName("TextArea");
        var pane = new JScrollPane(text);
        pane.setBounds(40, 40, 200, 200);
        text.disable();
        add(pane);
    }
}
