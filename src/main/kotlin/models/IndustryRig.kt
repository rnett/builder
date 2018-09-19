package models

import com.rnett.eve.ligraph.sde.*
import org.jetbrains.exposed.sql.transactions.transaction

enum class Security {
    NullSecWH, LowSec, HighSec;

    companion object {
        fun forSecStatus(secStatus: Double) = {
            when {
                secStatus >= 0.5 -> HighSec
                secStatus >= 0 -> LowSec
                else -> NullSecWH
            }
        }
    }

}

class Rig(val type: invtype) {
    val bonuses: List<BonusSet>

    fun appliesTo(t: invtype): Boolean = appliesCache.computeIfAbsent(Pair(this, t)) { bonuses.any { it.applies(t) } }

    val originalMeBonus: Double
    val originalTeBonus: Double

    val securityModifiers: Map<Security, Double>

    fun meBonus(sec: Security) = originalMeBonus * securityModifiers[sec]!!

    fun teBonus(sec: Security) = originalTeBonus * securityModifiers[sec]!!

    init {
        val attrs = transaction { type.invtype_dgmtypeattributes_type.map { Pair(it.attributeID, it.valueFloat!!.toDouble()) }.toMap() }

        originalMeBonus = -1 * (attrs[2594] ?: 0.0)
        originalTeBonus = -1 * (attrs[2593] ?: 0.0)

        securityModifiers = mapOf(
                Security.HighSec to attrs[2355]!!,
                Security.LowSec to attrs[2356]!!,
                Security.NullSecWH to attrs[2357]!!
        )

        bonuses = transaction { type.invtype_dgmtypeeffects_type.map { it.effectID }.toList() }
                .asSequence()
                .map { BonusSet.allBonuses[it] }.filter { it != null }.map { it!! }
                .toList()

        rigIDCache[type.typeID] = this
        rigTypeCache[type] = this

    }

    override fun equals(other: Any?): Boolean {
        if (other == null || other !is Rig)
            return false

        return type == other.type
    }

    override fun hashCode(): Int {
        var result = type.hashCode()
        result = 31 * result + bonuses.hashCode()
        result = 31 * result + originalMeBonus.hashCode()
        result = 31 * result + originalTeBonus.hashCode()
        result = 31 * result + securityModifiers.hashCode()
        return result
    }

    companion object {
        val rigIDCache: MutableMap<Int, Rig> = mutableMapOf()
        val rigTypeCache: MutableMap<invtype, Rig> = mutableMapOf()

        val appliesCache: MutableMap<Pair<Rig, invtype>, Boolean> = mutableMapOf()

        operator fun get(type: invtype): Rig {
            return if (rigTypeCache.containsKey(type))
                rigTypeCache[type]!!
            else
                Rig(type)
        }

        operator fun get(typeID: Int): Rig {
            return if (rigIDCache.containsKey(typeID))
                rigIDCache[typeID]!!
            else
                Rig[invtypes.fromID(typeID)!!]
        }

    }

}

class BonusSet(val meAttributeID: Int, val meEffectID: Int) {

    val categoryIDs: MutableList<Int> = mutableListOf()
    val groupIDs: MutableList<Int> = mutableListOf()

    val teAttributeID: Int
        get() = meAttributeID + 1
    val teEffectID: Int
        get() = meEffectID + 1

    val categories: List<invcategory> by lazy { transaction { categoryIDs.map { invcategories.findFromPKs(it)!! } } }
    val groups: List<invgroup> by lazy { transaction { groupIDs.map { invgroups.findFromPKs(it)!! } } }

    fun applies(type: invtype): Boolean = appliesCache.computeIfAbsent(Pair(this, type)) {
        groupIDs.contains(type.groupID) || categoryIDs.contains(transaction { type.group.categoryID })
    }

    fun categories(vararg categories: Int): BonusSet {
        this.categoryIDs.clear()
        this.categoryIDs.addAll(categories.toList())
        return this
    }

    fun groups(vararg groups: Int): BonusSet {
        this.groupIDs.clear()
        this.groupIDs.addAll(groups.toList())
        return this
    }

    init {
        bonuses.add(this)
        meBonuses[meEffectID] = this
        teBonuses[teEffectID] = this
    }

    companion object {

        val appliesCache: MutableMap<Pair<BonusSet, invtype>, Boolean> = mutableMapOf()

        val bonuses: MutableList<BonusSet> = mutableListOf()

        val meBonuses: MutableMap<Int, BonusSet> = mutableMapOf()
        val teBonuses: MutableMap<Int, BonusSet> = mutableMapOf()
        val allBonuses: Map<Int, BonusSet>
            get() = meBonuses + teBonuses

        init {
            BonusSet(2538, 6805) // Ship Modules, Ship Rigs, Personal Deployables, Implants, Cargo Containers
                    .categories(7, 22, 20, 2)

            BonusSet(2540, 6808) // Ammunition, Charges, Scripts
                    .categories(8)

            BonusSet(2542, 6810) // Drones, Fighters
                    .categories(18, 87)

            BonusSet(2544, 6812) // T1 Frigates, T1 Destroyers, Shuttles
                    .groups(31, 25, 420)

            BonusSet(2546, 6814) // T1 Cruisers, T1 Battlecruisers, Industrial Ships,    Mining Barges
                    .groups(1201, 26, 419, 28, 463)

            BonusSet(2548, 6816) // T1 Battleships, T1 Freighters,                                     Industrial Command Ships
                    .groups(27, 513, 941)

            BonusSet(2550, 6818) // T2 Frigates, T2 Destroyers, T3 Destroyers
                    .groups(324, 830, 831, 834, 893, 1283, 1527, 541, 1534, 1305)

            BonusSet(2552, 6820) // T2 Cruisers, T2 Battlecruisers, T2 Haulers, Exhumers, T3 Cruisers,T3 Subsystems
                    .categories(32)
                    .groups(358, 832, 833, 894, 906, 540, 380, 1202, 543, 963)

            BonusSet(2555, 6822) // T2 Battleships, Jump Freighters
                    .groups(900, 898, 902)

            BonusSet(2557, 6824) // T2 Components, Tools, Data Interfaces, T3 Components
                    .groups(334, 332, 716, 964)

            BonusSet(2559, 6826) // Capital Construction Components
                    .groups(873)

            BonusSet(2561, 6828) // Structure Components, Structure Modules, Upwell Structures, Starbase Structures, Fuel Blocks
                    .categories(65, 66, 23)
                    .groups(1136)

            BonusSet(2575, 6838) // Capital Ships
                    .groups(30, 485, 547, 659, 883, 1538)

            BonusSet(2592, 6840) // bonus that affects material of all ships being manufactured, for XL rigs
                    .categories(6, 32)

            BonusSet(2658, 6888) // T2 Capital Construction Components
                    .groups(913)
        }
    }

}





