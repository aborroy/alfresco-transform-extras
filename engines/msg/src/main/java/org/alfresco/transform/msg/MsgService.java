package org.alfresco.transform.msg;

import jakarta.mail.BodyPart;
import jakarta.mail.Message;
import jakarta.mail.Multipart;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.hsmf.MAPIMessage;
import org.apache.poi.hsmf.datatypes.AttachmentChunks;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Date;
import java.util.List;
import java.util.Properties;

@Slf4j
@Service
public class MsgService {

    /**
     * Convert an email file (.eml or .msg) to PDF via pandoc.
     *
     * @param inputFile      the source file
     * @param sourceMimetype the MIME type of the source (message/rfc822 or application/vnd.ms-outlook)
     * @return a temporary PDF File (caller must delete)
     */
    public File convertToPdf(File inputFile, String sourceMimetype) throws Exception {
        String subject = "";
        String from = "";
        String to = "";
        String date = "";
        String body = "";

        if ("message/rfc822".equals(sourceMimetype)) {
            EmailContent content = parseEml(inputFile);
            subject = content.subject;
            from = content.from;
            to = content.to;
            date = content.date;
            body = content.body;
        } else {
            EmailContent content = parseMsg(inputFile);
            subject = content.subject;
            from = content.from;
            to = content.to;
            date = content.date;
            body = content.body;
        }

        String markdown = buildMarkdown(subject, from, to, date, body);
        File mdFile = writeMarkdown(markdown);
        try {
            return runPandoc(mdFile);
        } finally {
            mdFile.delete();
        }
    }

    private EmailContent parseEml(File inputFile) throws Exception {
        Session session = Session.getDefaultInstance(new Properties());
        MimeMessage mimeMessage;
        try (FileInputStream fis = new FileInputStream(inputFile)) {
            mimeMessage = new MimeMessage(session, fis);
        }

        String subject = nullToEmpty(mimeMessage.getSubject());
        String from = addressesToString(mimeMessage.getFrom());
        String to = addressesToString(mimeMessage.getRecipients(Message.RecipientType.TO));
        Date sentDate = mimeMessage.getSentDate();
        String date = sentDate != null ? sentDate.toString() : "";
        String body = extractBodyFromMimeMessage(mimeMessage);

        return new EmailContent(subject, from, to, date, body);
    }

    private String extractBodyFromMimeMessage(MimeMessage mimeMessage) throws Exception {
        Object content = mimeMessage.getContent();
        if (content instanceof String) {
            return (String) content;
        }
        if (content instanceof Multipart) {
            Multipart multipart = (Multipart) content;
            String htmlFallback = null;
            for (int i = 0; i < multipart.getCount(); i++) {
                BodyPart part = multipart.getBodyPart(i);
                String mimeType = part.getContentType();
                if (mimeType != null && mimeType.toLowerCase().startsWith("text/plain")) {
                    return (String) part.getContent();
                }
                if (mimeType != null && mimeType.toLowerCase().startsWith("text/html")) {
                    htmlFallback = (String) part.getContent();
                }
            }
            if (htmlFallback != null) {
                return htmlFallback;
            }
        }
        return "";
    }

    private EmailContent parseMsg(File inputFile) throws Exception {
        try (MAPIMessage mapiMessage = new MAPIMessage(inputFile)) {
            String subject = getChunkSafely(mapiMessage::getSubject);
            String from = getChunkSafely(mapiMessage::getDisplayFrom);
            String to = getChunkSafely(mapiMessage::getDisplayTo);
            String date = "";
            try {
                java.util.Calendar calendar = mapiMessage.getMessageDate();
                if (calendar != null) {
                    date = calendar.getTime().toString();
                }
            } catch (Exception e) {
                log.debug("Could not read MSG date: {}", e.getMessage());
            }
            String body = getChunkSafely(mapiMessage::getTextBody);
            if (body == null || body.isBlank()) {
                body = getChunkSafely(mapiMessage::getHtmlBody);
            }
            return new EmailContent(
                    nullToEmpty(subject),
                    nullToEmpty(from),
                    nullToEmpty(to),
                    nullToEmpty(date),
                    nullToEmpty(body)
            );
        }
    }

    private String buildMarkdown(String subject, String from, String to, String date, String body) {
        return "# " + subject + "\n\n"
                + "**From:** " + from + "\n\n"
                + "**To:** " + to + "\n\n"
                + "**Date:** " + date + "\n\n"
                + "---\n\n"
                + body + "\n";
    }

    private File writeMarkdown(String markdown) throws IOException {
        File mdFile = File.createTempFile("msg-markdown-", ".md");
        try (FileWriter fw = new FileWriter(mdFile)) {
            fw.write(markdown);
        }
        return mdFile;
    }

    private File runPandoc(File mdFile) throws IOException, InterruptedException {
        File pdfFile = File.createTempFile("msg-output-", ".pdf");
        ProcessBuilder pb = new ProcessBuilder(
                "pandoc",
                mdFile.getAbsolutePath(),
                "-o", pdfFile.getAbsolutePath(),
                "--pdf-engine=xelatex"
        );
        pb.redirectErrorStream(true);
        Process process = pb.start();
        int exitCode = process.waitFor();
        if (exitCode != 0) {
            String output = new String(process.getInputStream().readAllBytes());
            throw new IOException("pandoc failed with exit code " + exitCode + ": " + output);
        }
        return pdfFile;
    }

    private String addressesToString(jakarta.mail.Address[] addresses) {
        if (addresses == null || addresses.length == 0) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < addresses.length; i++) {
            if (i > 0) sb.append(", ");
            sb.append(addresses[i].toString());
        }
        return sb.toString();
    }

    private String nullToEmpty(String value) {
        return value != null ? value : "";
    }

    @FunctionalInterface
    private interface StringSupplierChecked {
        String get() throws Exception;
    }

    private String getChunkSafely(StringSupplierChecked supplier) {
        try {
            return supplier.get();
        } catch (Exception e) {
            log.debug("Could not read MSG chunk: {}", e.getMessage());
            return "";
        }
    }

    private static class EmailContent {
        final String subject;
        final String from;
        final String to;
        final String date;
        final String body;

        EmailContent(String subject, String from, String to, String date, String body) {
            this.subject = subject;
            this.from = from;
            this.to = to;
            this.date = date;
            this.body = body;
        }
    }
}
