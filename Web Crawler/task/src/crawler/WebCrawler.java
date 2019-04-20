package crawler;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.io.*;
import java.net.*;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class WebCrawler extends JFrame {

    private final Pattern title_pattern = Pattern.compile("(?Uis).*?<title[^>]*>(.*?)(?:</title>.*?)?");
    private final Pattern charset_pattern = Pattern.compile("(?Ui)^.*?charset=([\\w-]+).*$");
    private final Pattern href_pattern = Pattern.compile("(?Ui).*?<a\\s(?:[^>]+//s)?href=([\"'])([^\"']+)\\1[^>]*>(?:.+?</a>)?.*?");
    private final Pattern relative_pattern = Pattern.compile("(?Ui)^([^/]+)");
    private final Pattern no_protocol_pattern = Pattern.compile("(?Ui)^//.+");
    private final Pattern nosource_slash_pattern = Pattern.compile("(?Ui)^/[^/]+.*");
    private final Pattern nosource_noslash_pattern = Pattern.compile("(?Ui)^[^/]+.+/[^/]*");

    public WebCrawler() {
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new BorderLayout());
        setTitle("Web Crawler");
        setVisible(true);

        final var url_label = new JLabel("URL:  ");
        final var url_text = new JTextField("");
        url_text.setName("UrlTextField");
        final var parse_button = new JButton("Parse");
        parse_button.setName("RunButton");
        final var title_label = new JLabel("Title:  ");
        final var title_label_text = new JLabel();
        title_label_text.setName("TitleLabel");
        final String[] column_names = {"URL", "Title"};
        final var title_table_model = new DefaultTableModel(column_names, 0);
        final var title_table = new JTable(title_table_model);
        title_table.setName("TitlesTable");
        final var export_label = new JLabel("Export: ");
        final var export_text = new JTextField();
        export_text.setName("ExportUrlTextField");
        final var export_button = new JButton("Save");
        export_button.setName("ExportButton");

        initUI(url_label, url_text, parse_button, title_label, title_label_text, title_table, export_label, export_text, export_button);
        initParser(parse_button, url_text, title_label_text, title_table_model);
        initSave(export_text, export_button, title_table_model);

        pack();
        setSize(800, 600);

        title_table.disable();
    }

    private void initUI(JLabel url_label, JTextField url_text, JButton parse_button, JLabel title_label, JLabel title_label_text, JTable title_table, JLabel export_label, JTextField export_text, JButton export_button) {
        final var user_input_pane = new JPanel();
        final var user_input_layout = new GroupLayout(user_input_pane);
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
                                .addComponent(url_text)
                                .addComponent(title_label_text)
                        )
                        .addComponent(parse_button)
        );
        user_input_layout.setVerticalGroup(
                user_input_layout.createSequentialGroup()
                        .addGroup(user_input_layout.createParallelGroup(GroupLayout.Alignment.BASELINE)
                                .addComponent(url_label)
                                .addComponent(url_text)
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

        final var export_input_pane = new JPanel();
        final var export_input_layout = new GroupLayout(export_input_pane);
        export_input_pane.setLayout(export_input_layout);
        export_input_layout.setAutoCreateGaps(true);
        export_input_layout.setAutoCreateContainerGaps(true);
        export_input_layout.setHorizontalGroup(
                export_input_layout.createSequentialGroup()
                        .addComponent(export_label)
                        .addComponent(export_text)
                        .addGroup(export_input_layout.createParallelGroup(GroupLayout.Alignment.LEADING)
                                .addComponent(export_button)
                        )
        );
        export_input_layout.setVerticalGroup(
                export_input_layout.createSequentialGroup()
                        .addGroup(export_input_layout.createParallelGroup(GroupLayout.Alignment.BASELINE)
                                .addComponent(export_label)
                                .addComponent(export_text)
                                .addComponent(export_button)
                        )
        );
        add(export_input_pane, BorderLayout.PAGE_END);
    }

    private void initParser(JButton parse_button, JTextField url_text, JLabel title_label_text, DefaultTableModel title_table_model) {
        parse_button.addActionListener(e -> {
            var buffered_input_stream = new AtomicReference<BufferedReader>();
            try {
                while (title_table_model.getRowCount() > 0) {
                    title_table_model.removeRow(title_table_model.getRowCount() - 1);
                }

                final var url = url_text.getText();
                final var input = new URI(url).toURL();
                var protocol = input.getProtocol();
                final var input_stream = input.openConnection();
                input_stream.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:63.0) Gecko/20100101 Firefox/63.0");
                var charset_matcher = new AtomicReference<>(charset_pattern.matcher(input_stream.getContentType()));
                var charset = new AtomicReference<>(StandardCharsets.UTF_8);
                if (charset_matcher.get().matches()) charset.set(getCharSet(charset_matcher.get().group(1)));
                buffered_input_stream.set(new BufferedReader(new InputStreamReader(input_stream.getInputStream(), charset.get())));
                var buffered_lines = buffered_input_stream.get().lines().toArray();

                var i = new AtomicInteger(0);
                while (i.get() < buffered_lines.length) {
                    var html = new StringBuilder(buffered_lines[i.get()].toString());
                    var title_matcher = new AtomicReference<>(title_pattern.matcher(buffered_lines[i.get()].toString()));
                    if (title_matcher.get().matches()) {
                        while (!html.toString().contains("</title>")) {
                            html.append(buffered_lines[i.getAndIncrement()].toString());
                        }
                        title_matcher.set(title_pattern.matcher(html.toString()));
                        if (title_matcher.get().matches()) title_label_text.setText(title_matcher.get().group(1));
                    }
                    var href_matcher = href_pattern.matcher(html);
                    while (href_matcher.find()) {
                        var link = new AtomicReference<>(href_matcher.group(2));
                        var validated = new AtomicBoolean(false);
                        var connection = new AtomicReference<URLConnection>();
                        try {
                            connection.set(new URI(link.get()).toURL().openConnection());
                        }
                        catch (IOException | IllegalArgumentException | URISyntaxException error) {
                            if (protocol.contains("http")) validateLink(link, url, protocol, validated);
                        }
                        finally {
                            try {
                                if (validated.get()) connection.set(new URI(link.get()).toURL().openConnection());
                                if (connection.get().getContentType().contains("text/html")) {
                                    charset_matcher.set(charset_pattern.matcher(connection.get().getContentType()));
                                    if (charset_matcher.get().matches()) charset.set(getCharSet(charset_matcher.get().group(1)));
                                    else charset.set(StandardCharsets.UTF_8);
                                    var buffered_link_input_stream = new BufferedReader(new InputStreamReader(connection.get().getURL().openStream(), charset.get()));
                                    var title = getLinkTitle(buffered_link_input_stream);
                                    String[] data = {link.get(), title};
                                    title_table_model.addRow(data);
                                    System.out.println(link.get() + "   |   " + title);
                                    buffered_link_input_stream.close();
                                }
                            }
                            catch (IOException | IllegalArgumentException | URISyntaxException | NullPointerException ignored) {}
                        }
                    }
                    i.getAndIncrement();
                }
            }
            catch (IOException | IllegalArgumentException | URISyntaxException error) {
                // TODO add error popup window
                error.printStackTrace();
            }
            finally {
                try {
                    if (buffered_input_stream.get() != null) buffered_input_stream.get().close();
                }
                catch (IOException ignored) {}
            }
        });
    }

    private void initSave(JTextField export_text, JButton export_button, DefaultTableModel title_table_model) {
        export_button.addActionListener(e -> {
            final var row_count = title_table_model.getRowCount();
            try {
                final var output = new OutputStreamWriter(new FileOutputStream(export_text.getText()), StandardCharsets.UTF_8);
                final var row = new AtomicInteger();
                while (row.get() < row_count) {
                    output.write(title_table_model.getValueAt(row.get(), 0).toString());
                    output.write("\n");
                    output.write(title_table_model.getValueAt(row.get(), 1).toString());
                    if (row.get() + 1 < row_count) output.write("\n");
                    row.getAndIncrement();
                }
                output.close();
            }
            catch (IOException error) {
                // TODO add error popup window
                error.printStackTrace();
            }
        });
    }

    private String getLinkTitle(BufferedReader buffered_link_input_stream) throws IOException {
        StringBuilder nextLine;
        var title_pattern = Pattern.compile("(?Uis).*?<title[^>]*>(.*?)(?:(</title>).*?)?");
        while ((nextLine = Optional.ofNullable(buffered_link_input_stream.readLine()).map(StringBuilder::new).orElse(null)) != null) {
            AtomicReference<Matcher> title_matcher = new AtomicReference<>(title_pattern.matcher(nextLine));
            if (title_matcher.get().matches()) {
                while (!nextLine.toString().contains("</title>")) {
                    var next = buffered_link_input_stream.readLine();
                    if (next != null) nextLine.append(next);
                    else throw new IOException("Invalid <title>: " + nextLine);
                }
                title_matcher.set(title_pattern.matcher(nextLine));
                if (title_matcher.get().matches()) return title_matcher.get().group(1);
            }
        }
        return"";
    }

    private void validateLink(AtomicReference<String> link, String url, String protocol, AtomicBoolean validated) {
        var relative_matcher = relative_pattern.matcher(link.get());
        var no_protocol_matcher = no_protocol_pattern.matcher(link.get());
        var nosource_slash_matcher = nosource_slash_pattern.matcher(link.get());
        var nosource_noslash_matcher = nosource_noslash_pattern.matcher(link.get());
        if (relative_matcher.matches() || nosource_noslash_matcher.matches()) {
            var index = url.lastIndexOf('/');
            if (url.charAt(index - 1) != '/') link.set(url.substring(0, index + 1) + link.get());
            else link.set(url + '/' + link.get());
            validated.set(true);
        }
        else if (no_protocol_matcher.matches()) {
            link.set(protocol + ':' + link.get());
            validated.set(true);
        }
        else if (nosource_slash_matcher.matches()) {
            var index = url.lastIndexOf('/');
            if (url.charAt(index - 1) != '/') link.set(url.substring(0, index) + link.get());
            else link.set(url + link.get());
            validated.set(true);
        }
    }

    private Charset getCharSet(String match) {
        switch (match) {
            case "us-ascii":
                return StandardCharsets.US_ASCII;
            case "iso-8859-1":
                return StandardCharsets.ISO_8859_1;
            case "utf-16":
                return StandardCharsets.UTF_16;
            case "utf-16be":
                return StandardCharsets.UTF_16BE;
            case "utf-16le":
                return StandardCharsets.UTF_16LE;
        }
        return StandardCharsets.UTF_8;
    }
}
