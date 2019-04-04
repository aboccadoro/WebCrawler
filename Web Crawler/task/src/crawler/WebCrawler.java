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

        final var url_label = new JLabel("URL:  ");
        final var site_url = new JTextField("http://www.example.com");
        site_url.setName("UrlTextField");
        final var parse_button = new JButton("Parse");
        parse_button.setName("RunButton");
        final var title_label = new JLabel("Title:  ");
        final var title_label_text = new JLabel();
        title_label_text.setName("TitleLabel");
        final var html_text = new JTextArea();

        initUI(url_label, site_url, parse_button, title_label, title_label_text, html_text);
        initParser(parse_button, site_url, title_label_text, html_text);

        pack();
        setSize(800, 600);

        html_text.disable();
        parse_button.doClick();
    }

    private void initUI(JLabel url_label, JTextField site_url, JButton parse_button, JLabel title_label, JLabel title_label_text, JTextArea html_text) {
        final var user_input_pane = new JPanel();
        var user_input_layout = new GroupLayout(user_input_pane);
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
                        .addComponent(parse_button)
        );
        user_input_layout.setVerticalGroup(
                user_input_layout.createSequentialGroup()
                        .addGroup(user_input_layout.createParallelGroup(GroupLayout.Alignment.BASELINE)
                                .addComponent(url_label)
                                .addComponent(site_url)
                                .addComponent(parse_button)
                        )
                        .addGroup(user_input_layout.createParallelGroup(GroupLayout.Alignment.BASELINE)
                                .addComponent(title_label)
                                .addComponent(title_label_text)
                        )
        );
        add(user_input_pane, BorderLayout.PAGE_START);

        html_text.setName("HtmlTextArea");
        html_text.setEditable(false);
        final var html_pane = new JScrollPane(html_text);
        add(html_pane, BorderLayout.CENTER);
    }

    private void initParser(JButton parse_button, JTextField site_url, JLabel title_label_text, JTextArea html_text) {
        parse_button.addActionListener(e -> {
            try {
                final String url = site_url.getText();

                final var inputStream = new URL(url).openStream();
                final var reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8));
                final var stringBuilder = new StringBuilder();

                String nextLine;
                var matched = new AtomicBoolean(false);
                var p = Pattern.compile(".*<title>(.*)</title>.*");
                while ((nextLine = reader.readLine()) != null) {
                    stringBuilder.append(nextLine);
                    stringBuilder.append(System.getProperty("line.separator"));
                    var m = p.matcher(nextLine);
                    if (!matched.get() && m.matches()) {
                        matched.set(true);
                        title_label_text.setText(m.group(1));
                    }
                }

                final var siteText = stringBuilder.toString();
                html_text.setText(siteText);
            }
            catch (IOException error) {
                html_text.setText("");
            }
        });
    }
}
