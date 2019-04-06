package crawler;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
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
                final var url = site_url.getText();
                var protocol_pattern = Pattern.compile("(?Ui)(.+)//.*");
                var protocol_matcher = protocol_pattern.matcher(url);
                if (!protocol_matcher.matches()) throw new IOException();
                var protocol = protocol_matcher.group(1);

                final var inputStream = new URL(url).openStream();
                final var reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8));

                StringBuilder nextLine;
                var title_pattern = Pattern.compile("(?Ui).*<title.*>(.*)</title>.*");
                var title_tag_pattern = Pattern.compile("(?Ui).*<title.*>.*");
                // TODO pattern match links
                var href_pattern = Pattern.compile("(?Ui)<[^>]+\\s+(?:href|src)=\\s*([\"'])([^\"']+)\\1[^>]*>(?:.+?</[^>]+>)?");
                while ((nextLine = Optional.ofNullable(reader.readLine()).map(StringBuilder::new).orElse(null)) != null) {
                    var title_tag_matcher = title_tag_pattern.matcher(nextLine);
                    if (title_tag_matcher.matches()) {
                        while (!nextLine.toString().contains("</title>")) {
                            var next = reader.readLine();
                            if (next != null) nextLine.append(next);
                            else throw new IOException();
                        }
                    }
                    var title_matcher = title_pattern.matcher(nextLine);
                    if (title_matcher.matches()) title_label_text.setText(title_matcher.group(1));
                    var href_matcher = href_pattern.matcher(nextLine);
                    if (href_matcher.matches()) {
                        // TODO Fix href/src assembly
                        var link = href_matcher.group(2);
                        var relative_pattern = Pattern.compile("(?Ui)([^/]+)");
                        var relative_matcher = relative_pattern.matcher(link);
                        var no_protocol_pattern = Pattern.compile("(?Ui)^//.+");
                        var no_protocol_matcher = no_protocol_pattern.matcher(link);
                        var nosource_slash_pattern = Pattern.compile("(?Ui)^/.+");
                        var nosource_slash_matcher = nosource_slash_pattern.matcher(link);
                        var nosource_noslash_pattern = Pattern.compile("(?Ui)^.+/.*");
                        var nosource_noslash_matcher = nosource_noslash_pattern.matcher(link);
                        if (relative_matcher.matches()) {
                            var stripped_source_link_pattern = Pattern.compile("(?Ui)(.+/)[^/]*");
                            var stripped_source_link_matcher = stripped_source_link_pattern.matcher(url);
                            if (stripped_source_link_matcher.matches()) link = stripped_source_link_matcher.group(1) + link;
                        }
                        else if (no_protocol_matcher.matches()) link = protocol + link;
                        else if (nosource_slash_matcher.matches()) {
                            var stripped_source_link_pattern = Pattern.compile("(?Ui)(.+)/[^/]*");
                            var stripped_source_link_matcher = stripped_source_link_pattern.matcher(url);
                            if (stripped_source_link_matcher.matches()) link = stripped_source_link_matcher.group(1) + link;
                        }
                        else if (nosource_noslash_matcher.matches()) {
                            var stripped_source_link_pattern = Pattern.compile("(?Ui)(.+/)[^/]*");
                            var stripped_source_link_matcher = stripped_source_link_pattern.matcher(url);
                            if (stripped_source_link_matcher.matches()) link = stripped_source_link_matcher.group(1) + link;
                        }
                        final var content = new URL(link).openConnection();
                        if (content.getContentType().contains("text/html")) {
                            String[] data = {link, getLinkTitle(link)};
                            title_table_model.addRow(data);
                        }
                    }
                }
            }
            catch (IOException error) {
                // TODO add error popup window
                error.printStackTrace();
            }
        });
    }

    private String getLinkTitle(String url) {
        try {
            final var inputStream = new URL(url).openStream();
            final var reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8));

            StringBuilder nextLine;
            var title_pattern = Pattern.compile("(?Ui).*<title.*>(.*)</title>.*");
            var title_tag_pattern = Pattern.compile("(?Ui).*<title.*>.*");
            while ((nextLine = Optional.ofNullable(reader.readLine()).map(StringBuilder::new).orElse(null)) != null) {
                var title_tag_matcher = title_tag_pattern.matcher(nextLine);
                if (title_tag_matcher.matches()) {
                    var next = reader.readLine();
                    if (next != null) nextLine.append(next);
                    else throw new IOException();
                }
                var title_matcher = title_pattern.matcher(nextLine.toString());
                if (title_matcher.matches()) return title_matcher.group(1);
            }
        }
        catch (IOException error) {
            // TODO add error popup window
        }
        return "";
    }
}
