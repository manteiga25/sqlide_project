package com.example.sqlide.Email;

import ch.qos.logback.classic.net.SocketReceiver;
import com.example.sqlide.AdvancedSearch.TableAdvancedSearchController;
import com.example.sqlide.EditSetController;
import com.example.sqlide.drivers.model.DataBase;
import com.jfoenix.controls.JFXTextField;
import com.jfoenix.controls.JFXToggleButton;
import jakarta.mail.*;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.PasswordField;
import javafx.scene.control.Separator;
import javafx.scene.control.ToolBar;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.web.HTMLEditor;
import javafx.scene.web.WebView;
import javafx.stage.Modality;
import javafx.stage.Stage;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.poi.ss.formula.functions.T;
import org.docx4j.convert.in.xhtml.XHTMLImporterImpl;
import org.docx4j.openpackaging.packages.WordprocessingMLPackage;
import org.docx4j.wml.Document;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.*;

import static com.example.sqlide.misc.path.createFile;
import static com.example.sqlide.popupWindow.handleWindow.ShowError;
import static com.example.sqlide.popupWindow.handleWindow.ShowInformation;

public class EmailController {

    @FXML
    private JFXToggleButton FetchEmailTool;
    @FXML
    private JFXTextField EmailField, SenderField, NameField, SubjectField, HostField, PortField;
    @FXML
    private PasswordField PasswordField;
    @FXML
    private ChoiceBox<String> SecurityBox;
    @FXML
    private ComboBox<String> ColumnEmailBox;
    @FXML
    private HTMLEditor EmailEditor;

    private WebView EmailWeb;

    private HashMap<String, String> QueryList = new HashMap<>();

    private final ObservableList<String> emails = FXCollections.observableArrayList();

    private HashMap<String, ArrayList<String>> TablesAndColumns;

    private final ArrayList<String> cards = new ArrayList<>();

    private Stage advancedFetcherstage;

    private DataBase db;

    public void setText(final String content) {
        EmailEditor.setHtmlText(content);
    }

    public void setDb(final DataBase db) {
        this.db = db;
    }

    /** Rotinas do esquema, repassadas ao construtor de consultas da janela de fetch. */
    private final ArrayList<com.example.sqlide.Metadata.RoutineMetadata> routines = new ArrayList<>();

    public void setRoutines(final List<com.example.sqlide.Metadata.RoutineMetadata> routines) {
        this.routines.clear();
        if (routines != null) this.routines.addAll(routines);
    }

    public void setTablesAndColumns(final HashMap<String, ArrayList<String>> TablesAndColumns) {
        this.TablesAndColumns = TablesAndColumns;
        for (final String table : TablesAndColumns.keySet()) {
            QueryList.put(table, "SELECT * FROM " + table);
        //    selected.put(table, TablesAndColumns.get(table));
        }
        setEmailBox();
        loadFetchStage();
    }

    @FXML
    private void initialize() {
        // STARTTLS na 587 é o que a maioria dos servidores espera; SSL na 465 é o legado.
        SecurityBox.getItems().addAll("STARTTLS", "SSL/TLS", "None");
        SecurityBox.getSelectionModel().selectFirst();
        SecurityBox.getSelectionModel().selectedItemProperty().addListener((_, _, mode) -> {
            if (PortField.getText() == null || PortField.getText().isBlank()) return;
            if ("SSL/TLS".equals(mode)) PortField.setText("465");
            else if ("STARTTLS".equals(mode)) PortField.setText("587");
        });

        EmailWeb = (WebView) EmailEditor.lookup(".web-view");
        EmailWeb.setPageFill(Color.valueOf("#1E1F22"));
        ToolBar toolbar = (ToolBar) EmailEditor.lookup(".tool-bar");

        final Button dataButton = new Button("Add Data");
        dataButton.setOnAction(_->loadInterface());

        toolbar.getItems().addAll(dataButton, new Separator());

        EmailEditor.setOnKeyReleased(_->{
            System.out.println(EmailEditor.getHtmlText());
        });
    }

    @FXML
    private void addEmail() {
        final String email = SenderField.getText();
        if (email.isEmpty() || emails.contains(email)) {
            ShowInformation("Invalid email", "You need to insert a valid email or new email.");
            SenderField.requestFocus();
            return;
        }
        SenderField.setText("");
        emails.add(email);
    }

    @FXML
    private void loadEditEmail() {
        if (emails.isEmpty()) {
            SenderField.requestFocus();
            ShowInformation("No Data", "You need to insert emails to edit");
            return;
        }
        try {

            // Carrega o arquivo FXML
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/example/sqlide/EditSet.fxml"));
            Parent root = loader.load();

            EditSetController secondaryController = loader.getController();

            // Criar um novo Stage para a subjanela
            Stage subStage = new Stage();
            subStage.setTitle("Send email");
            subStage.setScene(new Scene(root));
            secondaryController.InitializeController(emails);
            //secondaryController.DeleteColumnInnit(TableName.get(), ColumnsNames, subStage, this);

            // Opcional: definir a modalidade da subjanela
            subStage.initModality(Modality.APPLICATION_MODAL);

            // Mostrar a subjanela
            subStage.show();
        } catch (Exception e) {
            ShowError("Read asset", "Error to load asset file\n" + e.getMessage());
        }
    }

    @FXML
    private void openFetchStage() {
        advancedFetcherstage.show();
    }

    private void loadFetchStage() {
        try {

            // Carrega o arquivo FXML
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/example/sqlide/AdvancedSearch/AdvancedTableSearchStage.fxml"));
            Parent root = loader.load();

            TableAdvancedSearchController secondaryController = loader.getController();

            // Criar um novo Stage para a subjanela
            Stage subStage = new Stage();
            subStage.setTitle("Send email");
            subStage.setScene(new Scene(root));
            secondaryController.setStage(subStage);
            secondaryController.setRoutines(routines);
            if (db != null) secondaryController.setDialect(db.getSQLType());
            secondaryController.setTables(TablesAndColumns);

            subStage.showingProperty().addListener(_->{
                if (secondaryController.isClosedByUser()) {
                    QueryList = secondaryController.getQueryList();
              //      selected = secondaryController.getSelected();
                    setEmailBox();
                }
            });

            //secondaryController.DeleteColumnInnit(TableName.get(), ColumnsNames, subStage, this);

            // Opcional: definir a modalidade da subjanela
            subStage.initModality(Modality.APPLICATION_MODAL);

            // Mostrar a subjanela
            advancedFetcherstage = subStage;
            //subStage.show();
        } catch (Exception e) {
            ShowError("Read asset", "Error to load asset file\n" + e.getMessage());
        }
    }

    @FXML
    private void setFetchEmail() {
        ColumnEmailBox.setDisable(!FetchEmailTool.isSelected());
    }

    @FXML
    private void loadInterface() {
        try {

            // Carrega o arquivo FXML
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/example/sqlide/Email/insertDataStage.fxml"));
            Parent root = loader.load();

            insertDataController secondaryController = loader.getController();

            // Criar um novo Stage para a subjanela
            Stage subStage = new Stage();
            subStage.setTitle("Send email");
            subStage.setScene(new Scene(root));
            secondaryController.setData(TablesAndColumns);
            secondaryController.setInterface(this, subStage);
            //secondaryController.DeleteColumnInnit(TableName.get(), ColumnsNames, subStage, this);

            // Opcional: definir a modalidade da subjanela
            subStage.initModality(Modality.APPLICATION_MODAL);

            // Mostrar a subjanela
            subStage.show();
        } catch (Exception e) {
            ShowError("Read asset", "Error to load asset file\n" + e.getMessage());
        }
    }

    private void setEmailBox() {
        ColumnEmailBox.getItems().clear();
        for (final String table : QueryList.keySet()) {
            if (!QueryList.get(table).isEmpty()) {
                for (final String column : TablesAndColumns.get(table)) {
                    ColumnEmailBox.getItems().add(table + ":" + column);
                }
            }
        }
    }

    public void insertData(final String data) {
        final String insertScript =
                "var selection = window.getSelection();" +
                        "var range = selection.getRangeAt(0);" +
                        "var textNode = document.createTextNode('" + data + "');" +
                        "range.insertNode(textNode);";

        EmailWeb.getEngine().executeScript(insertScript);
    }

    @FXML
    private void send() {
        if (EmailField.getText().isEmpty()) {
            EmailField.requestFocus();
            ShowInformation("No data", "No email sender.");
            return;
        }
        // Sem servidor SMTP não há envio nenhum: antes o host estava fixo em
        // "smtp.example.com" no código e não havia forma de o mudar.
        if (HostField.getText() == null || HostField.getText().isBlank()) {
            HostField.requestFocus();
            ShowInformation("No server", "Fill in the SMTP host of your mail provider.");
            return;
        }
        if (!FetchEmailTool.isSelected() && emails.isEmpty()) {
            SenderField.requestFocus();
            ShowInformation("No data", "No emails to send.");
            return;
        } else if (FetchEmailTool.isSelected()
                && (ColumnEmailBox.getValue() == null || ColumnEmailBox.getValue().isEmpty())) {
            ColumnEmailBox.requestFocus();
            ShowInformation("No data", "No emails to send.");
            return;
        }
        optimize();

        // Lidos aqui porque os controlos da UI não podem ser tocados na thread de envio.
        final String template = EmailEditor.getHtmlText();
        final String from = EmailField.getText();
        final String subject = SubjectField.getText();
        final String user = NameField.getText();
        final String password = PasswordField.getText();
        final String emailColumn = FetchEmailTool.isSelected() ? ColumnEmailBox.getValue() : null;
        final Session session = createSession(user, password);

        Thread.ofVirtual().start(()->{
            final int buffer = db.buffer;
            int offset = 0;
            int sent = 0;
            boolean end = false;
            while (!end) {
                HashMap<String, ArrayList<HashMap<String, String>>> dataCopy = new HashMap<>();
                cards.clear();
                cards.addAll(Collections.nCopies(buffer, template));
                for (final String table : QueryList.keySet()) {
                    final String query = QueryList.get(table);
                    if (!query.isEmpty()) {
                        dataCopy.put(table, db.Fetcher().fetchDataMap(query, buffer, offset));
                        if (dataCopy.get(table) == null || dataCopy.get(table).isEmpty()) {
                            end = true;
                            break;
                        }
                    }
                }

                final ArrayList<String> emailsSend = new ArrayList<>();

                if (emailColumn != null) {
                    for (final String table : dataCopy.keySet()) {
                        for (final String column : dataCopy.get(table).getFirst().keySet()) {
                            if (emailColumn.equals(table + ":" + column)) {
                                for (final HashMap<String, String> row : dataCopy.get(table)) {
                                    emailsSend.add(row.get(column));
                                }
                            }
                        }
                    }
                } else {
                    // A página vem do offset corrente, que só avança no fim do ciclo.
                    final int startIndex = Math.min(offset, emails.size());
                    final int endIndex = Math.min(offset + buffer, emails.size());
                    emailsSend.addAll(emails.subList(startIndex, endIndex));
                }

                if (emailsSend.size() < buffer) {
                    end = true;
                }

                for (int cardIndex = 0; cardIndex < emailsSend.size(); cardIndex++) {
                    // Encadeia as substituições em copyCard: partindo sempre de card só a
                    // última coluna sobrevivia, e as restantes tags ficavam por preencher.
                    String copyCard = cards.get(cardIndex);
                    for (final String table : dataCopy.keySet()) {
                        final ArrayList<HashMap<String, String>> rows = dataCopy.get(table);
                        if (cardIndex >= rows.size()) continue;
                        for (final String column : rows.getFirst().keySet()) {
                            final String value = rows.get(cardIndex).get(column);
                            // replace literal em vez de replaceAll: os dados podem trazer
                            // $ ou \, que num replacement de regex corrompem o resultado.
                            copyCard = copyCard.replace("&lt;DataSrc=" + table + ":" + column + "/&gt;",
                                    value == null ? "" : value);
                        }
                    }
                    cards.set(cardIndex, copyCard);
                }

                if (emailsSend.isEmpty()) {
                    final int total = sent;
                    Platform.runLater(() -> ShowInformation("No Recipients",
                            total == 0 ? "No recipients specified for the email."
                                    : total + " email(s) sent."));
                    return;
                }

                try {
                    for (int index = 0; index < emailsSend.size(); index++) {
                        final String card = cards.get(index);
                        final String emailToSend = emailsSend.get(index);
                        if (emailToSend == null || emailToSend.isBlank()) continue;
                        MimeMessage message = new MimeMessage(session);
                        message.setFrom(new InternetAddress(from));
                        message.setSubject(subject, "UTF-8");
                        message.setSentDate(new java.util.Date());
                        // Set the modified HTML content (with placeholders replaced)
                        message.setContent(card, "text/html; charset=utf-8");
                        message.addRecipient(Message.RecipientType.TO, new InternetAddress(emailToSend));
                        Transport.send(message); // Enabled sending
                        sent++;
                    }
                } catch (Exception e) {
                    // Os diálogos são JavaFX: chamados desta thread rebentavam com
                    // IllegalStateException em vez de mostrarem o erro.
                    Platform.runLater(() -> ShowError("Error to send", "Error to send email.\n" + e.getMessage()));
                    return;
                }

                // O avanço da página é um por ciclo, não um por tabela: dentro do loop das
                // tabelas saltava buffer vezes o número de tabelas e perdiam-se linhas.
                offset += buffer;
            }

            final int total = sent;
            Platform.runLater(() -> ShowInformation("Finished", total + " email(s) sent."));
        });


    }

    /** Sessão SMTP a partir dos campos do formulário. */
    private Session createSession(final String user, final String password) {
        final String port = PortField.getText() == null || PortField.getText().isBlank()
                ? "587" : PortField.getText().trim();
        final String security = SecurityBox.getValue() == null ? "STARTTLS" : SecurityBox.getValue();
        final boolean authenticate = user != null && !user.isBlank();

        Properties props = new Properties();
        // Os valores têm de ser String: o jakarta.mail lê-os por getProperty(), que devolve
        // null para um Integer, e a porta acabava ignorada.
        props.put("mail.smtp.host", HostField.getText().trim());
        props.put("mail.smtp.port", port);
        props.put("mail.smtp.auth", String.valueOf(authenticate));

        switch (security) {
            case "SSL/TLS" -> {
                props.put("mail.smtp.ssl.enable", "true");
                props.put("mail.smtp.socketFactory.port", port);
                props.put("mail.smtp.socketFactory.class", "javax.net.ssl.SSLSocketFactory");
            }
            case "None" -> props.put("mail.smtp.starttls.enable", "false");
            default -> {
                props.put("mail.smtp.starttls.enable", "true");
                props.put("mail.smtp.starttls.required", "true");
            }
        }

        if (!authenticate) return Session.getInstance(props);

        return Session.getInstance(props, new Authenticator() {
            @Override
            protected PasswordAuthentication getPasswordAuthentication() {
                return new PasswordAuthentication(user, password);
            }
        });
    }

    private void optimize() {
        final String content = EmailEditor.getHtmlText();
        Iterator<String> iterator = QueryList.keySet().iterator(); // Iterador explícito

        while (iterator.hasNext()) {
            String table = iterator.next(); // Obtém a próxima chave
            if (!content.contains("&lt;DataSrc=" + table + ":") && !QueryList.get(table).isEmpty()) {
                if (!FetchEmailTool.isSelected()) {
                    iterator.remove(); // Remove usando o iterador
                } else if (!ColumnEmailBox.getValue().contains(table)) {
                    iterator.remove();
                }
            }
        }
    }

    @FXML
    private void ExportWord() throws IOException {
        final String path = createFile((Stage) EmailEditor.getScene().getWindow(), new String[]{"Word"}, new String[]{".docx"});
        Thread.ofVirtual().start(()-> {
            try {

                WordprocessingMLPackage wordMLPackage = WordprocessingMLPackage.createPackage();
// Configura o importer
                XHTMLImporterImpl xhtmlImporter = new XHTMLImporterImpl(wordMLPackage);
                xhtmlImporter.setHyperlinkStyle("Hyperlink");
// Importa o HTML
                // List<Object> objs = xhtmlImporter.convert(EmailEditor.getHtmlText());
                wordMLPackage.getMainDocumentPart().getContent().addAll(xhtmlImporter.convert(EmailEditor.getHtmlText(), null));
                // wordMLPackage.getMainDocumentPart().getContent().addAll(objs);
// Salva em docx
                wordMLPackage.save(new java.io.File(path));
            } catch (Exception e) {
                ShowError("Error to create", "Error to create .docx file\n" + e.getMessage());
            }
        });

    }

    @FXML
    private void ExportPDF() throws IOException {
    }
}
