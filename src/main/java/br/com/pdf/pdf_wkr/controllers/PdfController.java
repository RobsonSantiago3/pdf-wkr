package br.com.pdf.pdf_wkr.controllers;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
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
            // 1. Criação de arquivos temporários
            tempInput = Files.createTempFile("input-", ".pdf");
            tempOutput = Files.createTempFile("output-", ".pdf");
            file.transferTo(tempInput);

            // 2. Montagem do comando Ghostscript
            // Nota: Removi o "-f" antes do ICC, que causava erro de sintaxe
            List<String> command = new ArrayList<>();
            command.add("gs");
            command.add("-dPDFA");
            command.add("-dBATCH");
            command.add("-dNOPAUSE");
            command.add("-dNOOUTERSAVE");
            command.add("-sProcessColorModel=DeviceRGB");
            command.add("-sDEVICE=pdfwrite");
            command.add("-sPDFACompatibilityPolicy=1");
            command.add("-sOutputFile=" + tempOutput.toAbsolutePath());
            command.add(tempInput.toAbsolutePath().toString());

            ProcessBuilder pb = new ProcessBuilder(command);
            pb.redirectErrorStream(true); // Redireciona erro para o stream de saída
            
            log.info("Iniciando Ghostscript para o arquivo: {}", file.getOriginalFilename());
            Process process = pb.start();

            // 3. Captura do log do Ghostscript (Essencial para Debug)
            StringBuilder gsLogs = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    gsLogs.append(line).append("\n");
                }
            }

            // 4. Aguarda a finalização com timeout
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
                log.error("Logs do Ghostscript:\n{}", gsLogs.toString());
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                        .header("X-Error-Log", "Verifique os logs do servidor")
                        .build();
            }

        } catch (Exception e) {
            log.error("Falha crítica ao processar PDF: ", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        } finally {
            // 5. Limpeza rigorosa de arquivos temporários
            deleteTempFile(tempInput);
            deleteTempFile(tempOutput);
        }
    }

    private void deleteTempFile(Path path) {
        try {
            if (path != null) {
                Files.deleteIfExists(path);
            }
        } catch (Exception e) {
            log.warn("Não foi possível deletar arquivo temporário: {}", path);
        }
    }
}
