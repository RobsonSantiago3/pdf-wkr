package br.com.pdf.pdf_wkr.controllers;


import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

@RestController
@RequestMapping("/api/pdf")
@Slf4j
public class PdfController {

    @PostMapping("/convert-to-pdfa")
    public ResponseEntity<byte[]> convertToPdfA(@RequestParam("file") MultipartFile file) {
        Path tempInput = null;
        Path tempOutput = null;

        try {
            tempInput = Files.createTempFile("input-", ".pdf");
            tempOutput = Files.createTempFile("output-", ".pdf");
            file.transferTo(tempInput);

            // Comando Ghostscript para gerar PDF/A-1b incorporando fontes e cores
            ProcessBuilder pb = new ProcessBuilder(
                    "gs",
                    "-dPDFA",
                    "-dBATCH",
                    "-dNOPAUSE",
                    "-dNOOUTERSAVE",
                    "-sProcessColorModel=DeviceRGB",
                    "-sDEVICE=pdfwrite",
                    "-sPDFACompatibilityPolicy=1",
                    "-sOutputFile=" + tempOutput.toAbsolutePath(),
                    "-f", "/app/sRGB.icc", // Caminho definido no Dockerfile
                    tempInput.toAbsolutePath().toString()
            );

            pb.redirectErrorStream(true);
            Process process = pb.start();

            // Timeout de 60 segundos para evitar travamentos
            boolean finished = process.waitFor(60, TimeUnit.SECONDS);

            if (finished && process.exitValue() == 0) {
                byte[] bytes = Files.readAllBytes(tempOutput);
                log.info("Conversão concluída com sucesso.");

                return ResponseEntity.ok()
                        .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=pdfa_compliant.pdf")
                        .contentType(MediaType.APPLICATION_PDF)
                        .body(bytes);
            } else {
                log.error("Erro no Ghostscript. Exit code: {}", process.exitValue());
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
            }
        } catch (Exception e) {
            log.error("Falha ao processar PDF: ", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        } finally {
            // Limpeza de arquivos temporários
            try {
                if (tempInput != null) Files.deleteIfExists(tempInput);
                if (tempOutput != null) Files.deleteIfExists(tempOutput);
            } catch (Exception ignored) {}
        }
    }
}