package br.com.locasign.architecture

import com.tngtech.archunit.core.importer.ImportOption
import com.tngtech.archunit.junit.AnalyzeClasses
import com.tngtech.archunit.junit.ArchTest
import com.tngtech.archunit.lang.ArchRule
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses

/**
 * Validação automatizada das fronteiras arquiteturais do monólito modular hexagonal.
 *
 * As regras codificam rigorosamente as restrições descritas no `AGENTS.md` (§4.1, §4.3 e §4.6):
 * - Isolamento do Domínio (Kotlin puro, sem dependências de infraestrutura/frameworks nem de camadas externas).
 * - Camada de Aplicação agnóstica de frameworks e tecnologias de transporte/persistência.
 * - Direção de dependências: `interfaces -> app -> domain <- infra`.
 * - Isolamento modular e acoplamento estrito entre `lease`, `contract`, `notification` e `shared`.
 */
@AnalyzeClasses(
    packages = ["br.com.locasign"],
    importOptions = [ImportOption.DoNotIncludeTests::class],
)
class HexagonalArchitectureTest {

    @ArchTest
    val `camada domain nao depende de frameworks nem bibliotecas externas`: ArchRule =
        noClasses()
            .that().resideInAPackage("..domain..")
            .should().dependOnClassesThat()
            .resideInAnyPackage(
                "org.springframework..",
                "tools.jackson..",
                "com.fasterxml..",
                "org.slf4j..",
                "org.apache.kafka..",
                "java.sql..",
                "jakarta..",
            )
            .`as`("A camada de domínio deve ser Kotlin puro e não depender de Spring, Jackson, SLF4J, Kafka, SQL ou Jakarta")

    @ArchTest
    val `camada domain nao depende de app, interfaces ou infra`: ArchRule =
        noClasses()
            .that().resideInAPackage("..domain..")
            .should().dependOnClassesThat()
            .resideInAnyPackage("..app..", "..interfaces..", "..infra..")
            .`as`("A camada de domínio não deve depender de app, interfaces ou infra")

    @ArchTest
    val `camada app nao depende de frameworks de infraestrutura ou persistencia`: ArchRule =
        noClasses()
            .that().resideInAPackage("..app..")
            .should().dependOnClassesThat()
            .resideInAnyPackage(
                "org.springframework..",
                "tools.jackson..",
                "com.fasterxml..",
                "org.apache.kafka..",
                "java.sql..",
                "jakarta..",
            )
            .`as`("A camada de aplicação não deve depender de Spring, Jackson, Kafka, SQL ou Jakarta (apenas org.slf4j é permitido)")

    @ArchTest
    val `camada app nao depende de interfaces ou infra`: ArchRule =
        noClasses()
            .that().resideInAPackage("..app..")
            .should().dependOnClassesThat()
            .resideInAnyPackage("..infra..", "..interfaces..")
            .`as`("A camada de aplicação não deve depender de infra ou interfaces")

    @ArchTest
    val `camada interfaces nao depende de infra`: ArchRule =
        noClasses()
            .that().resideInAPackage("..interfaces..")
            .should().dependOnClassesThat()
            .resideInAPackage("..infra..")
            .`as`("A camada de interfaces não deve depender diretamente de infra")

    @ArchTest
    val `camada infra nao depende de interfaces`: ArchRule =
        noClasses()
            .that().resideInAPackage("..infra..")
            .should().dependOnClassesThat()
            .resideInAPackage("..interfaces..")
            .`as`("A camada de infra não deve depender de interfaces driving")

    @ArchTest
    val `modulo shared nao depende de modulos de negocio`: ArchRule =
        noClasses()
            .that().resideInAPackage("br.com.locasign.shared..")
            .should().dependOnClassesThat()
            .resideInAnyPackage(
                "br.com.locasign.lease..",
                "br.com.locasign.contract..",
                "br.com.locasign.notification..",
            )
            .`as`("O módulo shared é transversal e não deve depender de lease, contract ou notification")

    @ArchTest
    val `modulo lease nao depende de contract nem notification`: ArchRule =
        noClasses()
            .that().resideInAPackage("br.com.locasign.lease..")
            .should().dependOnClassesThat()
            .resideInAnyPackage(
                "br.com.locasign.contract..",
                "br.com.locasign.notification..",
            )
            .`as`("O módulo lease não deve depender de contract nem notification")

    @ArchTest
    val `modulo notification nao depende diretamente de outros modulos`: ArchRule =
        noClasses()
            .that().resideInAPackage("br.com.locasign.notification..")
            .should().dependOnClassesThat()
            .resideInAnyPackage(
                "br.com.locasign.lease..",
                "br.com.locasign.contract..",
            )
            .`as`("O módulo notification consome apenas eventos Kafka e não deve importar código de lease ou contract")

    @ArchTest
    val `modulo contract nao depende de notification`: ArchRule =
        noClasses()
            .that().resideInAPackage("br.com.locasign.contract..")
            .should().dependOnClassesThat()
            .resideInAPackage("br.com.locasign.notification..")
            .`as`("O módulo contract não deve depender de notification")

    @ArchTest
    val `modulo contract nao importa modelos nem VOs de lease exceto LeaseId`: ArchRule =
        noClasses()
            .that().resideInAPackage("br.com.locasign.contract..")
            .and().doNotHaveSimpleName("LeaseLookupAdapter")
            .and().doNotHaveSimpleName("LeaseActivationAdapter")
            .should().dependOnClassesThat()
            .resideInAnyPackage(
                "br.com.locasign.lease.domain.models..",
                "br.com.locasign.lease.domain.valueobjects.LeaseTerm..",
            )
            .`as`("O módulo contract só pode importar LeaseId de lease.domain, nunca outros modelos ou VOs (exceto a ponte LeaseBridgeAdapters)")

    @ArchTest
    val `modulo contract so acessa lease app via ponte LeaseBridgeAdapters`: ArchRule =
        noClasses()
            .that().resideInAPackage("br.com.locasign.contract..")
            .and().doNotHaveSimpleName("LeaseLookupAdapter")
            .and().doNotHaveSimpleName("LeaseActivationAdapter")
            .should().dependOnClassesThat()
            .resideInAPackage("br.com.locasign.lease.app..")
            .`as`("O acesso de contract a lease.app deve ocorrer exclusivamente via LeaseBridgeAdapters")

    @ArchTest
    val `modulo contract nunca depende de lease infra ou lease interfaces`: ArchRule =
        noClasses()
            .that().resideInAPackage("br.com.locasign.contract..")
            .should().dependOnClassesThat()
            .resideInAnyPackage(
                "br.com.locasign.lease.infra..",
                "br.com.locasign.lease.interfaces..",
            )
            .`as`("O módulo contract nunca deve depender de lease.infra ou lease.interfaces")
}
