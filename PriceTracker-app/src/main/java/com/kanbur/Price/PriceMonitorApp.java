package com.kanbur.Price;

import com.formdev.flatlaf.FlatDarculaLaf;
import com.formdev.flatlaf.FlatLightLaf;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.*;
import java.net.URI;
import java.net.URISyntaxException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class PriceMonitorApp {

    private JFrame frame;
    JTextField urlField;
    JTextField nameField;
    JTable table;
    DefaultTableModel tableModel;
    private List<Product> products;
    private JButton updateButton;
    private JLabel countdownLabel;
    private Timer countdownTimer;
    private int countdown;

    public static void main(String[] args) {
        try {
            UIManager.setLookAndFeel(new FlatLightLaf());
        } catch (UnsupportedLookAndFeelException e) {
            e.printStackTrace();
        }
        EventQueue.invokeLater(() -> {
            try {
                PriceMonitorApp window = new PriceMonitorApp();
                window.frame.setVisible(true);
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    public PriceMonitorApp() {
        FlatDarculaLaf.setup();
        UIManager.put("Button.arc", 999);
        products = loadProducts();
        initialize();
        updatePrices(); // Update prices on startup
    }

    public void initialize() {
        frame = new JFrame();
        frame.setTitle("Price Monitor");
        frame.setBounds(100, 100, 700, 500);  // Smaller initial window size
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.getContentPane().setLayout(new BorderLayout(0, 0));

        Font labelFont = new Font("Arial", Font.BOLD, 16);  // Larger font for labels
        Font fieldFont = new Font("Arial", Font.PLAIN, 16); // Larger font for text fields
        Font buttonFont = new Font("Arial", Font.BOLD, 16); // Larger font for buttons

        JPanel inputPanel = new JPanel();
        frame.getContentPane().add(inputPanel, BorderLayout.NORTH);
        inputPanel.setLayout(new GridLayout(2, 3, 10, 10));

        JLabel urlLabel = new JLabel("URL");
        urlLabel.setFont(labelFont);
        inputPanel.add(urlLabel);

        JLabel nameLabel = new JLabel("Name");
        nameLabel.setFont(labelFont);
        inputPanel.add(nameLabel);

        inputPanel.add(new JLabel()); // Empty cell for layout

        urlField = new JTextField();
        urlField.setFont(fieldFont);
        urlField.setPreferredSize(new Dimension(150, 30)); // Slightly larger text field
        inputPanel.add(urlField);
        urlField.setColumns(10);

        nameField = new JTextField();
        nameField.setFont(fieldFont);
        nameField.setPreferredSize(new Dimension(150, 30)); // Slightly larger text field
        inputPanel.add(nameField);
        nameField.setColumns(10);

        JButton addButton = new JButton("Add");
        addButton.setFont(buttonFont);
        addButton.setPreferredSize(new Dimension(100, 30)); // Slightly larger button
        addButton.addActionListener(e -> addProduct());
        inputPanel.add(addButton);

        JPanel centerPanel = new JPanel(new BorderLayout());
        frame.getContentPane().add(centerPanel, BorderLayout.CENTER);

        JScrollPane scrollPane = new JScrollPane();
        centerPanel.add(scrollPane, BorderLayout.CENTER);

        tableModel = new DefaultTableModel(
                new Object[][]{},
                new String[]{"Name", "Price", "Fetched At"}
        );
        table = new JTable(tableModel);
        table.setFont(fieldFont);
        table.setRowHeight(30); // Larger row height for better readability
        scrollPane.setViewportView(table);

        // Add a panel for buttons
        JPanel buttonPanel = new JPanel(new GridLayout(2, 1, 10, 10));
        centerPanel.add(buttonPanel, BorderLayout.EAST);

        JButton removeButton = new JButton("Remove");
        removeButton.setFont(buttonFont);
        removeButton.setPreferredSize(new Dimension(100, 30)); // Slightly larger button
        removeButton.addActionListener(e -> removeProduct());
        buttonPanel.add(removeButton);

        JButton goButton = new JButton("Go");
        goButton.setFont(buttonFont);
        goButton.setPreferredSize(new Dimension(100, 30)); // Slightly larger button
        goButton.addActionListener(e -> openSelectedUrl());
        buttonPanel.add(goButton);

        JPanel bottomPanel = new JPanel(new BorderLayout());
        frame.getContentPane().add(bottomPanel, BorderLayout.SOUTH);

        updateButton = new JButton("Update Prices");
        updateButton.setFont(buttonFont);
        updateButton.setPreferredSize(new Dimension(150, 30)); // Slightly larger button
        updateButton.addActionListener(e -> {
            updatePrices();
            startCountdown();
        });
        bottomPanel.add(updateButton, BorderLayout.NORTH);

        countdownLabel = new JLabel("Please wait 10 seconds for update.");
        countdownLabel.setFont(new Font("Arial", Font.BOLD, 18)); // Larger font size
        countdownLabel.setHorizontalAlignment(SwingConstants.CENTER);
        bottomPanel.add(countdownLabel, BorderLayout.CENTER);
    }

    void addProduct() {
        String url = urlField.getText().trim();
        String name = nameField.getText().trim();

        if (url.isEmpty() || name.isEmpty()) {
            JOptionPane.showMessageDialog(frame, "URL and Name cannot be empty!", "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }

        String cssClass = getCssClass(url);
        if (cssClass == null) {
            JOptionPane.showMessageDialog(frame, "Unsupported URL domain!", "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }

        products.add(new Product(url, name, cssClass));
        saveProducts();
        updatePrices();

        urlField.setText("");
        nameField.setText("");
    }

    String getCssClass(String url) {
        if (url.contains("akakce.com")) {
            return "pt_v8";
        } else if (url.contains("trendyol.com")) {
            return "prc-dsc";
        } else if (url.contains("amazon.com")) {
            return "a-price-whole";
        } else {
            return null;
        }
    }

    void updatePrices() {
        tableModel.setRowCount(0);
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

        for (Product product : products) {
            String price = checkPrice(product.getUrl(), product.getCssClass());
            String fetchedAt = sdf.format(new Date());
            if (price != null) {
                tableModel.addRow(new Object[]{product.getName(), price, fetchedAt});
            } else {
                tableModel.addRow(new Object[]{product.getName(), "Error fetching price", fetchedAt});
            }
        }
    }

    String checkPrice(String url, String cssClass) {
        try {
            Document doc = Jsoup.connect(url)
                    .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36")
                    .get();

            Element priceElement = doc.selectFirst("." + cssClass);
            return priceElement != null ? priceElement.text().trim() : null;
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }

     void saveProducts() {
        try (FileWriter file = new FileWriter("prices.json")) {
            file.write(new Gson().toJson(products));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    List<Product> loadProducts() {
        try (Reader reader = new FileReader("prices.json")) {
            return new Gson().fromJson(reader, new TypeToken<List<Product>>(){}.getType());
        } catch (FileNotFoundException e) {
            return new ArrayList<>();
        } catch (IOException e) {
            e.printStackTrace();
            return new ArrayList<>();
        }
    }

    private void startCountdown() {
        countdown = 10;
        updateButton.setEnabled(false);
        countdownLabel.setText("Please wait " + countdown + " seconds for update.");

        countdownTimer = new Timer(1000, new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                countdown--;
                if (countdown > 0) {
                    countdownLabel.setText("Please wait " + countdown + " seconds for update.");
                } else {
                    countdownLabel.setText("You can press the button now.");
                    updateButton.setEnabled(true);
                    countdownTimer.stop();
                }
            }
        });
        countdownTimer.start();
    }

    void removeProduct() {
        int selectedRow = table.getSelectedRow();
        if (selectedRow != -1) {
            String productName = (String) table.getValueAt(selectedRow, 0);
            for (int i = 0; i < products.size(); i++) {
                if (products.get(i).getName().equals(productName)) {
                    products.remove(i);
                    saveProducts();
                    updatePrices();
                    break;
                }
            }
        } else {
            JOptionPane.showMessageDialog(frame, "Please select a product to remove!", "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    public void openSelectedUrl() {
        int selectedRow = table.getSelectedRow();
        if (selectedRow == -1 || selectedRow >= table.getRowCount()) {
            JOptionPane.showMessageDialog(frame, "Please select a valid product to open its URL.", "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }

        // Assuming your Product class has a 'url' field
        String url = products.get(selectedRow).getUrl();
        if (url != null && !url.isEmpty()) {
            try {
                Desktop.getDesktop().browse(new URI(url));
            } catch (IOException | URISyntaxException e) {
                JOptionPane.showMessageDialog(frame, "Failed to open URL: " + url, "Error", JOptionPane.ERROR_MESSAGE);
                e.printStackTrace(); // Print stack trace for debugging purposes
            }
        } else {
            JOptionPane.showMessageDialog(frame, "Selected product does not have a valid URL.", "Error", JOptionPane.ERROR_MESSAGE);
        }
    }


    public class Product {
        private String url;
        private String name;
        private String cssClass;

        public Product(String url, String name, String cssClass) {
            this.url = url;
            this.name = name;
            this.cssClass = cssClass;
        }

        public String getUrl() {
            return url;
        }

        public String getName() {
            return name;
        }

        public String getCssClass() {
            return cssClass;
        }
    }
}