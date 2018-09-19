package pages

import com.rnett.ligraph.eve.contracts.ContractItem
import main.initalize
import org.jetbrains.exposed.sql.transactions.transaction

fun main(args: Array<String>) {
    initalize()

    val mutas = transaction { ContractItem.all().filter { it.type.typeName.contains("Abyssal", true) } }

}