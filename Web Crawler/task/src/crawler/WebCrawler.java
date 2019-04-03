package crawler;

import javax.swing.*;
import java.awt.*;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Pattern;

public class WebCrawler extends JFrame {

    public WebCrawler() {
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new BorderLayout());
        setTitle("Web Crawler");
        setVisible(true);

        var site_url = new JTextField("http://www.example.com");
        site_url.setName("UrlTextField");
        var download = new JButton("Get HTML");
        download.setName("RunButton");
        var url_label = new JLabel("URL:  ");
        var title_label = new JLabel("Title:  ");
        var title_label_text = new JLabel();
        title_label_text.setName("TitleLabel");

        var user_input_pane = new JPanel();
        GroupLayout user_input_layout = new GroupLayout(user_input_pane);
        user_input_pane.setLayout(user_input_layout);
        user_input_layout.setAutoCreateGaps(true);
        user_input_layout.setAutoCreateContainerGaps(true);
        user_input_layout.setHorizontalGroup(
                user_input_layout.createSequentialGroup()
                        .addGroup(user_input_layout.createParallelGroup(GroupLayout.Alignment.LEADING)
                                .addComponent(url_label)
                                .addComponent(title_label)
                        )
                        .addGroup(user_input_layout.createParallelGroup(GroupLayout.Alignment.LEADING)
                                .addComponent(site_url)
                                .addComponent(title_label_text)
                        )
                        .addComponent(download)
        );
        user_input_layout.setVerticalGroup(
                user_input_layout.createSequentialGroup()
                        .addGroup(user_input_layout.createParallelGroup(GroupLayout.Alignment.BASELINE)
                                .addComponent(url_label)
                                .addComponent(site_url)
                                .addComponent(download)
                        )
                        .addGroup(user_input_layout.createParallelGroup(GroupLayout.Alignment.BASELINE)
                                .addComponent(title_label)
                                .addComponent(title_label_text)
                        )
        );
        add(user_input_pane, BorderLayout.PAGE_START);

        var html = new JTextArea();
        html.setName("HtmlTextArea");
        html.setEditable(false);
        var html_pane = new JScrollPane(html);
        add(html_pane, BorderLayout.CENTER);

        download.addActionListener(e -> {
            try {
                final String url = site_url.getText();

                final var inputStream = new URL(url).openStream();
                final var reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8));
                final var stringBuilder = new StringBuilder();

                String nextLine;
                AtomicBoolean matched = new AtomicBoolean(false);
                var p = Pattern.compile(".*<title>(.*)</title>.*");
                while ((nextLine = reader.readLine()) != null) {
                    stringBuilder.append(nextLine);
                    stringBuilder.append(System.getProperty("line.separator"));
                    var m = p.matcher(nextLine);
                    if (!matched.get() && m.matches()) {
                        matched.set(true);
                        title_label_text.setText(m.group(1));
                    }
                    else if (m.matches()) System.out.println("multiple");
                }

                final var siteText = stringBuilder.toString();
                html.setText(siteText);
            }
            catch (IOException error) {
                html.setText("");
            }
        });

        pack();
        setSize(800, 600);

        html.disable();
        download.doClick();
    }
}
