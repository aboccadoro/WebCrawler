package crawler;

import javax.swing.*;
import java.awt.*;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.nio.charset.StandardCharsets;

public class WebCrawler extends JFrame {

    public WebCrawler() {
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new BorderLayout());
        setTitle("Web Crawler");
        setVisible(true);

        var site_url = new JTextField();
        site_url.setName("UrlTextField");
        var download = new JButton("Get HTML");
        download.setName("RunButton");

        var user_input_pane = new JPanel();
        GroupLayout user_input_layout = new GroupLayout(user_input_pane);
        user_input_pane.setLayout(user_input_layout);
        user_input_layout.setAutoCreateGaps(true);
        user_input_layout.setAutoCreateContainerGaps(true);
        user_input_layout.setHorizontalGroup(
                user_input_layout.createSequentialGroup()
                        .addComponent(site_url)
                        .addComponent(download)
                        .addGroup(user_input_layout.createParallelGroup(GroupLayout.Alignment.LEADING))
        );
        user_input_layout.setVerticalGroup(
                user_input_layout.createSequentialGroup()
                        .addGroup(user_input_layout.createParallelGroup(GroupLayout.Alignment.LEADING)
                                .addComponent(site_url)
                                .addComponent(download)
                        )
        );
        user_input_pane.add(site_url);
        user_input_pane.add(download);
        add(user_input_pane, BorderLayout.PAGE_START);

        var html = new JTextArea();
        html.setName("HtmlTextArea");
        html.setEditable(false);
        var html_pane = new JScrollPane(html);
        add(html_pane, BorderLayout.CENTER);

        download.addActionListener(e -> {
            try {
                final String url = site_url.getText();

                final InputStream inputStream = new URL(url).openStream();
                final BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8));
                final StringBuilder stringBuilder = new StringBuilder();

                String nextLine;
                while ((nextLine = reader.readLine()) != null) {
                    stringBuilder.append(nextLine);
                    stringBuilder.append(System.getProperty("line.separator"));
                }

                final String siteText = stringBuilder.toString();
                html.setText(siteText);
            }
            catch (IOException error) {
                html.setText("");
            }
        });

        pack();
        setSize(800, 600);

        html.disable();
    }
}
