package org.alfresco.transform.liteparse;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

@Slf4j
@Service
public class LiteparseService {

    public File convert(File inputFile, String targetMimetype) throws IOException, InterruptedException {
        Path outputDir = Files.createTempDirectory("liteparse-out-");
        boolean markdown = "text/markdown".equals(targetMimetype);
        File outputFile = outputDir.resolve(markdown ? "output.md" : "output.txt").toFile();

        String inputPath  = inputFile.getAbsolutePath();
        String outputPath = outputFile.getAbsolutePath();

        // Both modes iterate result.pages — result.text is unreliable for non-PDF formats (e.g. empty for PPTX)
        String script = markdown
                ? "from liteparse import LiteParse; "
                  + "parser = LiteParse(); "
                  + "result = parser.parse(r'" + inputPath + "'); "
                  + "lines = []; "
                  + "[lines.extend(['## Page ' + str(p.page_num), '', p.text.strip(), '', '---', '']) for p in result.pages]; "
                  + "open(r'" + outputPath + "', 'w', encoding='utf-8').write('\\n'.join(lines))"
                : "from liteparse import LiteParse; "
                  + "parser = LiteParse(); "
                  + "result = parser.parse(r'" + inputPath + "'); "
                  + "open(r'" + outputPath + "', 'w', encoding='utf-8').write('\\n\\n'.join(p.text.strip() for p in result.pages if p.text.strip()))";

        log.debug("LiteparseService: {} -> {} ({})", inputPath, outputPath, targetMimetype);

        ProcessBuilder pb = new ProcessBuilder("python3", "-c", script);
        pb.redirectErrorStream(true);
        Process process = pb.start();
        byte[] output = process.getInputStream().readAllBytes();
        int exitCode = process.waitFor();

        if (exitCode != 0) {
            throw new IOException("liteparse process failed (exit " + exitCode + "): " + new String(output));
        }

        if (!outputFile.exists() || outputFile.length() == 0) {
            throw new IOException("liteparse did not produce output at " + outputFile);
        }

        return outputFile;
    }
}
