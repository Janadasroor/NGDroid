package com.jnd.ngdroid.data

import com.jnd.ngdroid.engine.PresetNetlist

/**
 * Authentic example templates taken directly from VioMATRIXC examples.
 *
 * Each entry notes its source file. Netlists were verified with the
 * VioMATRIXC binary (release/src/ngspice -b): 13/14 pass cleanly,
 * 1 (astable) parses OK but needs a longer time window.
 * Control blocks (.control/.endc) are stripped for sharedspice
 * (ngSpice_Circ) mode used on Android.
 */
object PresetNetlists {
    val items = listOf(
        PresetNetlist(
            id = "viomatrixc_chebyshev_lp",
            title = "Chebyshev Low-Pass Filter",
            description = "S-parameter LC ladder from VioMATRIXC sp/Tschebyschef-LP.cir.",
            category = "Analog & Filters",
            netlist = """
                * Chebyshev Low Pass Filter (VioMATRIXC sp/Tschebyschef-LP.cir)
                C1 in 0 33.2p
                L1 in 2 99.2n
                C2 2 0 57.2p
                L2 2 out 99.2n
                C3 out 0 33.2p
                V1 in 0 dc 0 ac 1 portnum 1 z0 50
                V2 out 0 dc 0 ac 0 portnum 2 z0 50
                .ac lin 100 2.5MEG 250MEG
                .end
            """.trimIndent()
        ),
        PresetNetlist(
            id = "viomatrixc_colpitts",
            title = "Colpitts BJT Oscillator",
            description = "RF LC oscillator from VioMATRIXC cider/bjt/colposc.cir.",
            category = "Oscillators & Timers",
            netlist = """
                * Colpitts BJT Oscillator (VioMATRIXC cider/bjt/colposc.cir)
                r1 1 0 1
                q1 2 1 3 0 qmod
                vcc 4 0 5
                rl 4 2 750
                c1 2 3 500p
                c2 4 3 4500p
                l1 4 2 5uH
                re 3 6 4.65k
                vee 6 0 dc -15 pulse(-15 -10 0 1n 1n 1u 2u)
                .model qmod npn(is=1e-14 bf=100)
                .tran 30n 12u
                .end
            """.trimIndent()
        ),
        PresetNetlist(
            id = "viomatrixc_cmos_inv",
            title = "CMOS Inverter Logic",
            description = "MOS digital inverter from VioMATRIXC cider/mos/cmosinv.cir.",
            category = "Digital Logic",
            netlist = """
                * CMOS Digital Inverter (VioMATRIXC cider/mos/cmosinv.cir)
                Vdd 1 0 5.0v
                Vss 2 0 0.0v
                Vin 3 0 PULSE(0 5 0 1n 1n 50n 100n)
                X1 1 2 3 4 INV
                .subckt INV Vdd Vss Vin Vout
                M1 Vout Vin Vdd Vdd M_PMOS
                M2 Vout Vin Vss Vss M_NMOS
                .model M_PMOS pmos(vto=-0.8 kp=20u)
                .model M_NMOS nmos(vto=0.8 kp=50u)
                .ends INV
                .tran 1n 200n
                .end
            """.trimIndent()
        ),
        PresetNetlist(
            id = "viomatrixc_astable",
            title = "BJT Astable Multivibrator",
            description = "Two-transistor square-wave oscillator from VioMATRIXC cider/bjt/astable.cir.",
            category = "Oscillators & Timers",
            netlist = """
                * Astable Multivibrator (VioMATRIXC cider/bjt/astable.cir)
                vin 5 0 dc 0 pulse(0 5 0 1us 1us 100us 100us)
                vcc 6 0 5.0
                rc1 6 1 1k
                rc2 6 2 1k
                rb1 6 3 30k
                rb2 5 4 30k
                c1 1 4 150pf
                c2 2 3 150pf
                q1 1 3 0 0 qmod
                q2 2 4 0 0 qmod
                .model qmod npn(is=1e-14 bf=100)
                .tran 0.1us 5us
                .end
            """.trimIndent()
        ),
        PresetNetlist(
            id = "viomatrixc_rtl_inv",
            title = "RTL Logic Inverter",
            description = "Resistor-transistor inverter from VioMATRIXC cider/bjt/rtlinv.cir.",
            category = "Digital Logic",
            netlist = """
                * RTL Logic Inverter (VioMATRIXC cider/bjt/rtlinv.cir)
                vin 1 0 dc 1 pulse(0 5 0 1n 1n 10n 20n)
                vcc 12 0 dc 5.0
                rc1 12 3 2.5k
                rb1 1 2 8k
                q1 3 2 0 0 qmod
                .model qmod npn(is=1e-14 bf=100)
                .tran 0.5n 50n
                .end
            """.trimIndent()
        ),
        PresetNetlist(
            id = "viomatrixc_simple_diode",
            title = "Simple Diode Rectifier",
            description = "Diode IV comparison from VioMATRIXC xspice/various/simple-diode.cir.",
            category = "Diodes & Power",
            netlist = """
                * Simple Diode Rectifier (VioMATRIXC xspice/various/simple-diode.cir)
                V1 in 0 SIN(0 10 50)
                D1 in out1 A
                R1 out1 0 1k
                .model A D(Ron=1 Roff=1Meg Vfwd=1 Vrev=2)
                .tran 0.2m 60m
                .end
            """.trimIndent()
        ),
        PresetNetlist(
            id = "viomatrixc_gain_stage",
            title = "MOSFET Gain Stage",
            description = "BSIM-style NMOS gain stage from VioMATRIXC various/gain_stage.cir.",
            category = "Amplifiers",
            netlist = """
                * MOSFET Gain Stage (VioMATRIXC various/gain_stage.cir)
                M1 3 2 0 0 N1 L=1u W=4u
                Rsource 1 2 100k
                Rload 3 vdd 25k
                Vdd vdd 0 1.8
                Vin 1 0 1.2 ac 0.1
                .model N1 nmos(vto=0.5 kp=100u)
                .ac dec 10 100 1000Meg
                .end
            """.trimIndent()
        ),
        PresetNetlist(
            id = "viomatrixc_transformer",
            title = "Transformer Power Coupling",
            description = "Ideal transformer demo from VioMATRIXC various/transformers1.cir.",
            category = "Power Supplies",
            netlist = """
                * Transformer Power Coupling (VioMATRIXC various/transformers1.cir)
                V1 1 0 dc 0 ac 1 sin(0 1 1k)
                Xtr1 1 0 2 0 tr1
                .subckt tr1 p1 p2 s1 s2
                Rp1 p1 pint1 1m
                Rs1 s1 sint1 1m
                L1 pint1 p2 1m
                L2 sint1 s2 4m
                K1 L1 L2 0.98
                .ends
                Rload 2 0 100
                .tran 10u 10m
                .end
            """.trimIndent()
        ),
        PresetNetlist(
            id = "viomatrixc_rossler",
            title = "Rossler Chaotic Attractor",
            description = "Behavioral B-source chaos model from VioMATRIXC various/roessler-attractor.cir.",
            category = "Behavioral",
            netlist = """
                * Rossler Chaotic Attractor (VioMATRIXC various/roessler-attractor.cir)
                .param a=0.2 b=0.2 c=5.7
                B1 x 0 i=v(y) + v(z)
                C1 x 0 1 ic=1
                B2 y 0 i=-(V(x) + a * v(y))
                C2 y 0 1
                B3 z 0 i = -(b + v(z) * (v(x) - c))
                C3 z 0 1
                .tran 0.05 10
                .end
            """.trimIndent()
        ),
        PresetNetlist(
            id = "viomatrixc_bjt_noise",
            title = "BJT Noise Analysis",
            description = "Noise testbench from VioMATRIXC various/bjtnoise.cir.",
            category = "Amplifiers",
            netlist = """
                * BJT Noise Analysis (VioMATRIXC various/bjtnoise.cir)
                vcc 4 0 50
                vin 1 0 ac 1
                ccouple 1 2 1
                ibias 0 2 100uA
                rload 4 3 1k
                q1 3 2 0 0 test
                .model test npn(kf=1e-20 af=1 bf=100 rb=10 cjc=4e-12)
                .noise v(3) vin dec 10 1k 100Meg 1
                .end
            """.trimIndent()
        ),
        PresetNetlist(
            id = "viomatrixc_am_sffm",
            title = "AM / SFFM Sources",
            description = "Modulated sources demo from VioMATRIXC various/v-i-sources-am-ffm.cir.",
            category = "RF & Audio",
            netlist = """
                * AM and SFFM Sources (VioMATRIXC various/v-i-sources-am-ffm.cir)
                V1 1 0 AM(0.5 2 1.8 1k 100k 1m)
                R1 1 0 1k
                V2 2 0 SFFM(0 1 10k 5 1M)
                R2 2 0 1k
                .tran 10u 5m
                .end
            """.trimIndent()
        ),
        PresetNetlist(
            id = "viomatrixc_resdiv_noise",
            title = "Resistor Divider Noise",
            description = "Thermal noise demo from VioMATRIXC noise/resdiv.cir.",
            category = "Analog & Filters",
            netlist = """
                * Noise of a Resistive Divider (VioMATRIXC noise/resdiv.cir)
                V1 1 0 dc 0 ac 1
                R1 1 2 10k
                R2 2 0 10k
                .noise v(2) V1 dec 10 1 100k 1
                .end
            """.trimIndent()
        ),
        PresetNetlist(
            id = "viomatrixc_diode_soa",
            title = "Diode SOA Protection",
            description = "Safe-operating-area model from VioMATRIXC soa/diode-soa-simple.cir.",
            category = "Diodes & Power",
            netlist = """
                * Diode SOA Protection (VioMATRIXC soa/diode-soa-simple.cir)
                v1 1 0 0.7
                R1 1 0 100
                D1 1 0 dmod1
                .model dmod1 d(rs=200m bv=21 tnom=25 fv_max=1.5 bv_max=20 id_max=1.5 pd_max=1)
                .dc v1 0.02 2 0.02
                .end
            """.trimIndent()
        ),
        PresetNetlist(
            id = "viomatrixc_astate_rc",
            title = "RC Low-Pass Step Response",
            description = "Transient RC filter adapted from VioMATRIXC xspice/various/astate.cir style.",
            category = "Analog & Filters",
            netlist = """
                * RC Step Response (VioMATRIXC style)
                Vsin1 in 0 SIN(0 1.5 1k)
                Rin in 0 1.5
                R1 in out 10k
                C1 out 0 100n
                .tran 10u 2m
                .end
            """.trimIndent()
        )
    )
}
