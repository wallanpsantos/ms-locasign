package br.com.locasign.contract.infra.storage

import br.com.locasign.contract.app.ports.out.integration.SignedDocumentStoragePort
import br.com.locasign.contract.domain.valueobjects.ContractId
import br.com.locasign.shared.infra.config.LocaSignProperties
import org.springframework.stereotype.Component
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

/**
 * Adaptador de armazenamento físico que implementa [SignedDocumentStoragePort] gravando o PDF assinado em sistema de arquivos local.
 *
 * **Responsabilidade:**
 * - Garantir gravação atômica via arquivo temporário e `StandardCopyOption.ATOMIC_MOVE`, impedindo corrupção de arquivos em falhas abruptas.
 * - Centralizar o diretório de arquivo com base nas propriedades da aplicação (`locasign.archive.directory`).
 */
@Component
class FileSystemSignedDocumentStorage(properties: LocaSignProperties) : SignedDocumentStoragePort {

    private val root: Path = Path.of(properties.archive.directory).toAbsolutePath().normalize()

    override fun store(contractId: ContractId, bytes: ByteArray): String {
        Files.createDirectories(root)
        val target = root.resolve("$contractId.pdf")
        val temporary = Files.createTempFile(root, "$contractId-", ".tmp")
        try {
            Files.write(temporary, bytes)
            Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
        } finally {
            Files.deleteIfExists(temporary)
        }
        return target.toString()
    }
}
