import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableColumn;
import java.awt.BorderLayout;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

class Instrucao {
    String op, dest, src1, src2;

    Instrucao(String op, String dest, String src1, String src2) {
        this.op = op;
        this.dest = dest;
        this.src1 = src1;
        this.src2 = src2;
    }
}


public class Tomasulo {

    static JTable tabelaInstrucoes;
    static JTextField[] camposFP = new JTextField[6]; // F0..F5
    static JTextField campoLatLD;
    static JTextField campoLatADD;
    static JTextField campoLatMULT;


    static float[] fp;  
    static List<Instrucao> instrucoes; // aqui tenho as linhas do codigo
    static int latLD;
    static int latADD;
    static int latMULT;

    static int cicloAtual = 0;
    static JLabel labelCiclo;
    static int indiceProxInstrucao = 0;

    static RS[] loadStations  = new RS[2];      // estacoes reservas
    static RS[] addStations   = new RS[3];
    static RS[] multStations  = new RS[2];

    static String[] produtorRegistro;           // para cada Fx, quem vai produzir (nome da RS)


    static JTable tabelaRS;
    static DefaultTableModel modeloRS;




    private static void criarTabelaInstrucoes(JFrame janela) {
        String[] colunas = {"Operacao", "Destino", "Fonte 1", "Fonte 2"};
        DefaultTableModel modelo = new DefaultTableModel(colunas, 6);
        tabelaInstrucoes = new JTable(modelo);

        String[] ops = {"", "LD", "ADD", "MULT"};
        String[] regs = {"", "F0", "F1", "F2", "F3", "F4", "F5"};


        TableColumn colOp = tabelaInstrucoes.getColumnModel().getColumn(0); //operadores
        colOp.setCellEditor(new DefaultCellEditor(new JComboBox<>(ops)));

        for (int i = 1; i <= 3; i++) {
            TableColumn col = tabelaInstrucoes.getColumnModel().getColumn(i); // registradores
            col.setCellEditor(new DefaultCellEditor(new JComboBox<>(regs)));
        }

        JScrollPane scroll = new JScrollPane(tabelaInstrucoes);
        JPanel painelTabela = new JPanel(new BorderLayout());
        painelTabela.setBorder(BorderFactory.createTitledBorder("Instrucoes"));
        painelTabela.add(scroll, BorderLayout.CENTER);





        JButton botaoExecute = new JButton("Execute");
        JButton botaoProxCiclo = new JButton("+1 ciclo");
        botaoExecute.addActionListener(e -> {
            System.out.println("Executar pressionado!");

            fp = new float[camposFP.length];        // captura fp
            for (int i = 0; i < camposFP.length; i++) {
                String texto = camposFP[i].getText().trim();
                if (!texto.isEmpty()) {
                    fp[i] = Float.parseFloat(texto);
                }
            }

            DefaultTableModel modeloT = (DefaultTableModel) tabelaInstrucoes.getModel();
            int linhas = modeloT.getRowCount();
           instrucoes = new ArrayList<>();

            for (int i = 0; i < linhas; i++) {
                String op = (String) modeloT.getValueAt(i, 0);
                String dest = (String) modeloT.getValueAt(i, 1);
                String fonte1 = (String) modeloT.getValueAt(i, 2);
                String fonte2 = (String) modeloT.getValueAt(i, 3);

                if (op == null || op.isBlank()) continue;

                Instrucao instr = new Instrucao(op, dest, fonte1, fonte2);
                instrucoes.add(instr);
            }

            latLD   = Integer.parseInt(campoLatLD.getText().trim());
            latADD  = Integer.parseInt(campoLatADD.getText().trim());
            latMULT = Integer.parseInt(campoLatMULT.getText().trim());

            cicloAtual=0;
            indiceProxInstrucao=0;

            if (labelCiclo != null) {
                labelCiclo.setText("Ciclo atual: 0");
            }

            inicializarRS();
            produtorRegistro = new String[fp.length];
            for (int i = 0; i < produtorRegistro.length; i++) {
                produtorRegistro[i] = null; // ninguém produz nada no início
            }
            atualizarTabelaRS();

            //listarEstadoAtual();
        });
        botaoProxCiclo.addActionListener(e -> avancarCiclo());
        JPanel painelBotoes = new JPanel();
        painelBotoes.add(botaoExecute);
        painelBotoes.add(botaoProxCiclo);
        painelTabela.add(painelBotoes, BorderLayout.SOUTH);
        janela.add(painelTabela, BorderLayout.CENTER);

        configurarInstrucoesIniciais();
    }

    private static void configurarInstrucoesIniciais() {
        DefaultTableModel modeloTabela = (DefaultTableModel) tabelaInstrucoes.getModel();

        // Linha 0: LD F1, F0      (F1 depende de F0)
        modeloTabela.setValueAt("LD",  0, 0);
        modeloTabela.setValueAt("F1",  0, 1);
        modeloTabela.setValueAt("F0",  0, 2);
        modeloTabela.setValueAt("",    0, 3);

        // Linha 1: LD F2, F1      (RAW: F2 depende de F1 produzido na linha 0)
        modeloTabela.setValueAt("LD",  1, 0);
        modeloTabela.setValueAt("F2",  1, 1);
        modeloTabela.setValueAt("F1",  1, 2);
        modeloTabela.setValueAt("",    1, 3);

        // Linha 2: ADD F3, F1, F2 (RAW: usa F1 e F2 produzidos antes)
        modeloTabela.setValueAt("ADD", 2, 0);
        modeloTabela.setValueAt("F3",  2, 1);
        modeloTabela.setValueAt("F1",  2, 2);
        modeloTabela.setValueAt("F2",  2, 3);

        // Linha 3: LD F1, F2      (WAR: sobrescreve F1 após ele ter sido lido na linha 2)
        modeloTabela.setValueAt("LD",  3, 0);
        modeloTabela.setValueAt("F1",  3, 1);
        modeloTabela.setValueAt("F2",  3, 2);
        modeloTabela.setValueAt("",    3, 3);

        // Linha 4: MULT F4, F3, F2 (RAW: depende de F3 e F2)
        modeloTabela.setValueAt("MULT", 4, 0);
        modeloTabela.setValueAt("F4",   4, 1);
        modeloTabela.setValueAt("F3",   4, 2);
        modeloTabela.setValueAt("F2",   4, 3);

        // Linha 5: ADD F5, F4, F0 (RAW: depende de F4 produzido na linha 4)
        modeloTabela.setValueAt("ADD", 5, 0);
        modeloTabela.setValueAt("F5",  5, 1);
        modeloTabela.setValueAt("F4",  5, 2);
        modeloTabela.setValueAt("F0",  5, 3);
    }


    private static void criarInterfaceFPRegisters(JFrame janela) {
        JPanel painelFP = new JPanel();
        painelFP.setLayout(new BoxLayout(painelFP, BoxLayout.Y_AXIS));
        painelFP.setBorder(BorderFactory.createTitledBorder("FP Registers"));

        String[] nomes = {"F0", "F1", "F2", "F3", "F4", "F5"};

        for (int i = 0; i < nomes.length; i++) {
            JPanel linha = new JPanel(new BorderLayout(5, 5));
            JLabel label = new JLabel(nomes[i]);
            camposFP[i] = new JTextField(8);
            linha.add(label, BorderLayout.WEST);
            linha.add(camposFP[i], BorderLayout.CENTER);
            painelFP.add(linha);
        }

        JPanel painelDireita = new JPanel(new BorderLayout());
        painelDireita.add(painelFP, BorderLayout.NORTH);

        JPanel painelLatencias = criarPainelLatenciasOperacoes();
        painelDireita.add(painelLatencias, BorderLayout.SOUTH);
        janela.add(painelDireita, BorderLayout.EAST);


        configurarFPRegistersIniciais();
    }

    private static JPanel criarPainelLatenciasOperacoes() {
        JPanel painelLat = new JPanel(new java.awt.GridLayout(3, 2, 5, 5));
        painelLat.setBorder(BorderFactory.createTitledBorder("Latencia (Clock)"));

        painelLat.add(new JLabel("LD"));
        campoLatLD = new JTextField("2", 4);
        painelLat.add(campoLatLD);

        painelLat.add(new JLabel("ADD"));
        campoLatADD = new JTextField("4", 4);
        painelLat.add(campoLatADD);

        painelLat.add(new JLabel("MULT"));
        campoLatMULT = new JTextField("10", 4);
        painelLat.add(campoLatMULT);

        return painelLat;
    }

    private static void configurarFPRegistersIniciais() { // preset aleatorio
        Random random = new Random();
        for (int i = 0; i < camposFP.length; i++) {
            float valor = 1 + random.nextInt(20); // de 1 a 20
            camposFP[i].setText(String.valueOf(valor));
        }
    }

    private static void listarEstadoAtual() {

        System.out.println("\n===== ESTADO ATUAL DO SIMULADOR =====");

        // Exibir FP Registers
        if (fp != null) {
            System.out.println("\nFP Registers:");
            for (int i = 0; i < fp.length; i++) {
                System.out.println("F" + i + " = " + fp[i]);
            }
        } else {
            System.out.println("\nFP Registers ainda não foram carregados.");
        }

        // Exibir instruções
        if (instrucoes != null) {
            System.out.println("\nInstruções:");
            for (int i = 0; i < instrucoes.size(); i++) {
                Instrucao inst = instrucoes.get(i);
                System.out.printf("%d: %s %s %s %s\n",
                    i,
                    inst.op,
                    inst.dest,
                    inst.src1,
                    inst.src2
                );
            }
        } else {
            System.out.println("\nNenhuma instrução carregada ainda.");
        }

        // Listando clocks
      
        System.out.println("\nValor dos clocks:");
        System.out.println(latLD);
        System.out.println(latADD);
        System.out.println(latMULT);

        System.out.println("====================================\n");
    }




    /* Ate nesse ponto criamos a tabela de instrucoes, o fp register e a latencia de clock de cada operando, 
    com isso temos o fp e intrucoes para comecarmos as proxima partes*/


    static class RS {
        String name;    
        boolean busy; 
        String op;    
        Float Vj, Vk;    
        String Qj, Qk;  
        int remaining;   
        boolean prontoParaWrite;

        RS(String name) {
            this.name = name;
            limpar();
        }

        void limpar() {
            busy = false;
            op = null;
            Vj = Vk = null;
            Qj = Qk = null;
            remaining = 0;
            prontoParaWrite = false;
        }
    }

    private static void inicializarRS() {
        // LOAD
        for (int i = 0; i < loadStations.length; i++) {
            loadStations[i] = new RS("Load" + (i+1));
        }
        // ADD/SUB
        for (int i = 0; i < addStations.length; i++) {
            addStations[i] = new RS("Add" + (i+1));
        }
        // MULT
        for (int i = 0; i < multStations.length; i++) {
            multStations[i] = new RS("Mult" + (i+1));
        }
    }

    private static void atualizarTabelaRS() {
        if (modeloRS == null) return;

        modeloRS.setRowCount(0); // limpa todas as linhas

        RS[] todas = {
            loadStations[0], loadStations[1],
            addStations[0], addStations[1], addStations[2],
            multStations[0], multStations[1]
        };

        for (RS r : todas) {
            if (r == null) continue;
            modeloRS.addRow(new Object[]{
                r.name,
                r.busy ? "YES" : "NO",
                r.op != null ? r.op : "",
                r.Vj != null ? r.Vj : "",
                r.Vk != null ? r.Vk : "",
                r.Qj != null ? r.Qj : "",
                r.Qk != null ? r.Qk : "",
                r.remaining
            });
        }
    }




    private static void avancarCiclo() {
        cicloAtual++;
        if (labelCiclo != null) {
            labelCiclo.setText("Ciclo atual: " + cicloAtual);
        }

        System.out.println("\n=== CICLO " + cicloAtual + " ===");
        emitirUmaInstrucao();
        executarOperacoes();
        escreverResultados();
        atualizarTabelaRS();        
    }

    private static void emitirUmaInstrucao() {
        if (instrucoes == null || instrucoes.isEmpty()) {
            System.out.println("Nenhuma instrucao carregada");
            return;
        }

        if (indiceProxInstrucao >= instrucoes.size()) {
            System.out.println("todas as instrucoes ja foram emitidas");
            return;
        }

        Instrucao inst = instrucoes.get(indiceProxInstrucao);
        String op = inst.op;

        RS[] alvo;

        if (op.equals("LD")) {
            alvo = loadStations;
        } else if (op.equals("ADD")) {
            alvo = addStations;
        } else if (op.equals("MULT")) {
            alvo = multStations;
        } else {
            System.out.println("operacao nao suportada: " + op);
            indiceProxInstrucao++;
            return;
        }

        RS livre = null;        // procura pra ver se tem uma estacao livre, as estacoes estao dentro de alvo ja
        for (RS rs : alvo) {
            if (!rs.busy) {
                livre = rs;
                break;
            }
        }

        if (livre == null) {
            System.out.println("Nenhuma RS livre para operacao " + op + ". Issue bloqueado neste ciclo.");
            return; 
        }

        livre.busy = true;
        livre.op = op;

        int idx1 = indiceRegistrador(inst.src1); // fonte 1
        if (idx1 >= 0) {
            if (produtorRegistro[idx1] == null) {
                // ninguém vai produzir , o valor já está em FP
                livre.Vj = fp[idx1];
                livre.Qj = null;
            } else {
                // alguém ainda vai produzir ,  dependência RAW
                livre.Vj = null;
                livre.Qj = produtorRegistro[idx1];
            }
        } else {
            livre.Vj = null;
            livre.Qj = null;
        }

        int idx2 = indiceRegistrador(inst.src2);    // fonte 2
        if (idx2 >= 0) {
            if (produtorRegistro[idx2] == null) {
                livre.Vk = fp[idx2];
                livre.Qk = null;
            } else {
                livre.Vk = null;
                livre.Qk = produtorRegistro[idx2];
            }
        } else {
            livre.Vk = null;
            livre.Qk = null;
        }

        int idxDest = indiceRegistrador(inst.dest);
        if (idxDest >= 0) {
            produtorRegistro[idxDest] = livre.name; // ex: produtorRegistro[3] = "Add1"
        }
        livre.remaining = 0;

        System.out.println("Emitida instrucao: " + op + " " + inst.dest + " " + inst.src1 + " " + inst.src2 + " para " + livre.name);
        indiceProxInstrucao++;
    }

    private static void executarOperacoes() {
        System.out.println("---- Execucao ----");

        // LOAD
        for (RS rs : loadStations) {
            if (!rs.busy) continue;

            // pode comecar a executar?
            if (rs.remaining == 0 && rs.Qj == null && !rs.prontoParaWrite) {
                rs.remaining = latLD;  // latencia inteira
                System.out.println(rs.name + " comecou Execucao (LD)");
            }

            // esta executando?
            if (rs.remaining > 0) {
                rs.remaining--;
                System.out.println(rs.name + " executando... (resta " + rs.remaining + ")");

                if (rs.remaining == 0) {
                    rs.prontoParaWrite = true;  // terminou execucao, vai escrever no proximo ciclo
                    System.out.println(rs.name + " terminou execucao, aguardando Write Result.");
                }
            }
        }

        // ADD
        for (RS rs : addStations) {
            if (!rs.busy) continue;

            if (rs.remaining == 0 && rs.Qj == null && rs.Qk == null && !rs.prontoParaWrite) {
                rs.remaining = latADD;
                System.out.println(rs.name + " comecou Execucao (ADD)");
            }

            if (rs.remaining > 0) {
                rs.remaining--;
                System.out.println(rs.name + " executando... (resta " + rs.remaining + ")");

                if (rs.remaining == 0) {
                    rs.prontoParaWrite = true;
                    System.out.println(rs.name + " terminou execucao, aguardando Write Result.");
                }
            }
        }

        // MULT
        for (RS rs : multStations) {
            if (!rs.busy) continue;

            if (rs.remaining == 0 && rs.Qj == null && rs.Qk == null && !rs.prontoParaWrite) {
                rs.remaining = latMULT;
                System.out.println(rs.name + " comecou Execucao (MULT)");
            }

            if (rs.remaining > 0) {
                rs.remaining--;
                System.out.println(rs.name + " executando... (resta " + rs.remaining + ")");

                if (rs.remaining == 0) {
                    rs.prontoParaWrite = true;
                    System.out.println(rs.name + " terminou execucao, aguardando Write Result.");
                }
            }
        }
    }


    private static void escreverResultados() {      //cdb
        System.out.println("---- Write Result ----");

        RS[] todas = {
            loadStations[0], loadStations[1],
            addStations[0], addStations[1], addStations[2],
            multStations[0], multStations[1]
        };

        for (RS rs : todas) {
            if (rs == null || !rs.busy || !rs.prontoParaWrite) continue;

            // 1) calcula resultado
            float resultado = 0f;

            if ("LD".equals(rs.op)) {
                resultado = (rs.Vj != null) ? rs.Vj : 0f;
            } else if ("ADD".equals(rs.op)) {
                float vj = (rs.Vj != null) ? rs.Vj : 0f;
                float vk = (rs.Vk != null) ? rs.Vk : 0f;
                resultado = vj + vk;
            } else if ("MULT".equals(rs.op)) {
                float vj = (rs.Vj != null) ? rs.Vj : 0f;
                float vk = (rs.Vk != null) ? rs.Vk : 0f;
                resultado = vj * vk;
            }

            System.out.println(rs.name + " escrevendo resultado no CDB: " + resultado);

            // 2) broadcast para outras RS que dependem deste resultado
            for (RS r2 : todas) {
                if (r2 == null || !r2.busy) continue;

                if (rs.name.equals(r2.Qj)) {
                    r2.Vj = resultado;
                    r2.Qj = null;
                }
                if (rs.name.equals(r2.Qk)) {
                    r2.Vk = resultado;
                    r2.Qk = null;
                }
            }

            // 3) atualizar FP registers: quem estava marcado como sendo produzido por essa RS
            if (produtorRegistro != null && fp != null) {
                for (int i = 0; i < produtorRegistro.length; i++) {
                    if (rs.name.equals(produtorRegistro[i])) {
                        fp[i] = resultado;
                        produtorRegistro[i] = null;
                        System.out.println("F" + i + " atualizado para " + resultado);
                    }
                }
            }

        
            atualizarCamposFP();
            rs.limpar();
        }
    }

    private static void atualizarCamposFP() {
        if (fp == null || camposFP == null) return;

        for (int i = 0; i < fp.length && i < camposFP.length; i++) {
            camposFP[i].setText(String.valueOf(fp[i]));
        }
    }

    private static Float obterValorRegistrador(String reg) {
        if (reg == null || reg.isBlank()) return null;
        if (!reg.startsWith("F")) return null;
        int idx = Integer.parseInt(reg.substring(1));
        if (fp == null || idx < 0 || idx >= fp.length) return null;
        return fp[idx]; // to pegando o valor digitado no fp register
    }

    private static int indiceRegistrador(String reg) {
        if (reg == null || reg.isBlank()) return -1;
        if (!reg.startsWith("F")) return -1;
        try {
            return Integer.parseInt(reg.substring(1));
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    private static JPanel criarPainelRS() {
        String[] colunas = {"Name", "Busy", "Op", "Vj", "Vk", "Qj", "Qk", "Time"};
        DefaultTableModel modeloRS = new DefaultTableModel(colunas, 7);
        tabelaRS = new JTable(modeloRS);

        JPanel painel = new JPanel(new BorderLayout());
        painel.setBorder(BorderFactory.createTitledBorder("Reservation Stations"));
        painel.add(new JScrollPane(tabelaRS), BorderLayout.CENTER);

        return painel;
    }

    private static void criarInterfaceRS(JFrame janela) {
        String[] colunas = {"RS", "Busy", "Op", "Vj", "Vk", "Qj", "Qk", "Rem"};
        modeloRS = new DefaultTableModel(colunas, 0);
        tabelaRS = new JTable(modeloRS);

        JScrollPane scrollRS = new JScrollPane(tabelaRS);
        scrollRS.setBorder(BorderFactory.createTitledBorder("Reservation Stations"));

        janela.add(scrollRS, BorderLayout.SOUTH);
    }

    /* falta implementar especulacao de desvios, rob e comit em ordem */



    
    public static void main(String[] args) {
        JFrame janela = new JFrame("Simulador Tomasulo");
        janela.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        janela.setSize(900, 600);
        janela.setLayout(new BorderLayout());

        criarTabelaInstrucoes(janela);     // centro (instruções)
        criarInterfaceFPRegisters(janela); // direita (FP registers)
        criarInterfaceRS(janela);

        labelCiclo = new JLabel("Ciclo atual: 0");
        JPanel painelStatus = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT));
        painelStatus.add(labelCiclo);
        janela.add(painelStatus, BorderLayout.NORTH);   // topo da janela

        janela.setLocationRelativeTo(null);
        janela.setVisible(true);
    }
}
