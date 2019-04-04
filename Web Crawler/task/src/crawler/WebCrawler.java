package crawler;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
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
        final var site_url = new JTextField("https://www.wikipedia.org/");
        site_url.setName("UrlTextField");
        final var parse_button = new JButton("Parse");
        parse_button.setName("RunButton");
        final var title_label = new JLabel("Title:  ");
        final var title_label_text = new JLabel();
        title_label_text.setName("TitleLabel");
        String[] column_names = {"URL", "Title"};
        DefaultTableModel title_table_model = new DefaultTableModel(column_names, 0);
        final var title_table = new JTable(title_table_model);
        title_table.setName("TitlesTable");

        initUI(url_label, site_url, parse_button, title_label, title_label_text, title_table);
        initParser(parse_button, site_url, title_label_text, title_table_model);

        pack();
        setSize(800, 600);

        title_table.disable();
        parse_button.doClick();
    }

    private void initUI(JLabel url_label, JTextField site_url, JButton parse_button, JLabel title_label, JLabel title_label_text, JTable title_table) {
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

        final var title_table_pane = new JScrollPane(title_table);
        add(title_table_pane, BorderLayout.CENTER);
    }

    private void initParser(JButton parse_button, JTextField site_url, JLabel title_label_text, DefaultTableModel title_table_model) {
        parse_button.addActionListener(e -> {
            try {
                final String url = site_url.getText();

                final var inputStream = new URL(url).openStream();
                final var reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8));

                String nextLine;
                String link;
                var title_matched = new AtomicBoolean(false);
                var title_pattern = Pattern.compile("(?Ui).*<title>(.*)</title>.*");
                // TODO pattern match links
                var href_pattern = Pattern.compile("(?Ui)<[^>]*\\s*href\\s*=\\s*(\"(?:[^\"]*\")|'[^']*'|(?:[^'\">\\s]+))[^>]*>.+?</[^>]+>");
                while ((nextLine = reader.readLine()) != null) {
                    var title_matcher = title_pattern.matcher(nextLine);
                    if (!title_matched.get() && title_matcher.matches()) {
                        title_matched.set(true);
                        title_label_text.setText(title_matcher.group(1));
                    }
                    var href_matcher = href_pattern.matcher(nextLine);
                    if (href_matcher.matches()) {
                        // TODO add protocol
                        link = href_matcher.group(1);
                        String[] data = {link.substring(1, link.length() - 1), getLinkTitle(link)};
                        title_table_model.addRow(data);
                    }
                }
            }
            catch (IOException error) {
                // TODO add error popup window
            }
        });
    }

    private String getLinkTitle(String url) {
        try {
            final var inputStream = new URL(url).openStream();
            final var reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8));

            String nextLine;
            var title_pattern = Pattern.compile("(?Ui).*<title>(.*)</title>.*");
            while ((nextLine = reader.readLine()) != null) {
                System.out.println("made it");
                var title_matcher = title_pattern.matcher(nextLine);
                if (title_matcher.matches()) return title_matcher.group(1);
            }
        }
        catch (IOException error) {
            // TODO add error popup window
        }
        return "";
    }
}
