package Codigo;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.TitledBorder;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

// ==================================================================================
//                                ESTRUTURAS DE DADOS
// ==================================================================================

class EntradaROB {
    int id;
    boolean busy;
    String tipo;
    String dest;        // Destino Arquitetural OU Alvo do Salto
    float valor;        // Valor calculado
    boolean ready;
    
    // Especulação
    boolean ehDesvio;
    boolean desvioTomado;
    int enderecoAlvo;

    EntradaROB(int id) { this.id = id; limpar(); }

    void limpar() {
        busy = false; tipo = ""; dest = ""; valor = 0f; ready = false;
        ehDesvio = false; desvioTomado = false; enderecoAlvo = -1;
    }
}

class Instrucao {
    String op, dest, src1, src2;
    Instrucao(String op, String dest, String src1, String src2) {
        this.op = op; this.dest = dest; this.src1 = src1; this.src2 = src2;
    }
}

public class Tomasulo {

    // --- CORES E FONTES (UI DESIGN) ---
    static final Color COR_FUNDO = new Color(245, 245, 250);
    static final Color COR_PRIMARY = new Color(60, 100, 220); 
    static final Color COR_ACCENT = new Color(255, 100, 100); 
    static final Color COR_SUCCESS = new Color(46, 204, 113); 
    static final Color COR_HEADER_TABLE = new Color(230, 230, 240);
    static final Font FONT_TITLE = new Font("Segoe UI", Font.BOLD, 16);
    static final Font FONT_METRIC = new Font("Consolas", Font.BOLD, 22);
    static final Font FONT_NORMAL = new Font("Segoe UI", Font.PLAIN, 12);

    // --- COMPONENTES UI ---
    static JTable tabelaInstrucoes, tabelaROB, tabelaRS;
    static DefaultTableModel modeloInstrucoes, modeloROB, modeloRS;
    static JTextField[] camposFP = new JTextField[6];
    static JTextField campoLatLD, campoLatADD, campoLatMULT;
    
    // Métricas UI
    static JLabel lblCicloVal, lblIPCVal, lblBolhasVal;

    // --- DADOS DO PROCESSADOR ---
    static float[] fp;
    static List<Instrucao> instrucoes;
    static int latLD, latADD, latMULT;
    
    static int cicloAtual = 0;
    static int indiceProxInstrucao = 0;
    static int instrucoesCommitadas = 0;
    static int ciclosDeBolha = 0;

    static RS[] loadStations = new RS[2];
    static RS[] addStations = new RS[3];
    static RS[] multStations = new RS[2];
    static String[] produtorRegistro;
    static EntradaROB[] rob;
    
    static int robHead = 0, robTail = 0, itensNoRob = 0;
    static final int TAM_ROB = 10;

    // --- CLASSE RS (Nested) ---
    static class RS {
        String name; boolean busy; String op; Float Vj, Vk; String Qj, Qk;
        int remaining; boolean prontoParaWrite; int destRobId;

        RS(String name) { this.name = name; limpar(); }
        void limpar() {
            busy = false; op = null; Vj = Vk = null; Qj = Qk = null;
            remaining = 0; prontoParaWrite = false; destRobId = -1;
        }
    }

    // ==================================================================================
    //                                  INTERFACE GRÁFICA (MAIN)
    // ==================================================================================

    public static void main(String[] args) {
        try {
            for (UIManager.LookAndFeelInfo info : UIManager.getInstalledLookAndFeels()) {
                if ("Nimbus".equals(info.getName())) {
                    UIManager.setLookAndFeel(info.getClassName());
                    break;
                }
            }
            UIManager.put("Table.alternateRowColor", new Color(245, 245, 252));
        } catch (Exception e) { e.printStackTrace(); }

        JFrame janela = new JFrame("Simulador Tomasulo :: Arquitetura Superescalar");
        janela.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        janela.setSize(1300, 850);
        janela.setLayout(new BorderLayout());
        janela.getContentPane().setBackground(COR_FUNDO);

        janela.add(criarPainelTopo(), BorderLayout.NORTH);

        JPanel painelHardware = new JPanel(new GridBagLayout()); 
        painelHardware.setBackground(COR_FUNDO);
        configurarLayoutHardware(painelHardware);

        JSplitPane splitPrincipal = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, 
                criarPainelInstrucoes(), 
                new JScrollPane(painelHardware));
        splitPrincipal.setDividerLocation(450); 
        splitPrincipal.setResizeWeight(0.3);
        splitPrincipal.setBorder(null);
        
        janela.add(splitPrincipal, BorderLayout.CENTER);
        janela.add(criarPainelControles(), BorderLayout.SOUTH);

        inicializarRS();
        inicializarROB();
        configurarInstrucoesIniciais(); 
        
        janela.setLocationRelativeTo(null);
        janela.setVisible(true);
    }

    // --- PAINÉIS UI ---

    private static JPanel criarPainelTopo() {
        JPanel p = new JPanel(new FlowLayout(FlowLayout.CENTER, 30, 15));
        p.setBackground(Color.WHITE);
        p.setBorder(BorderFactory.createMatteBorder(0, 0, 2, 0, new Color(220, 220, 230)));

        p.add(criarCardMetrica("CICLO ATUAL", lblCicloVal = new JLabel("0"), COR_PRIMARY));
        p.add(criarCardMetrica("IPC (Inst/Ciclo)", lblIPCVal = new JLabel("0.00"), COR_SUCCESS));
        p.add(criarCardMetrica("BOLHAS (Stalls)", lblBolhasVal = new JLabel("0"), COR_ACCENT));
        return p;
    }

    private static JPanel criarCardMetrica(String titulo, JLabel valorLabel, Color cor) {
        JPanel card = new JPanel(new BorderLayout());
        card.setBackground(Color.WHITE);
        card.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(new Color(230, 230, 230), 1),
            new EmptyBorder(5, 20, 5, 20)
        ));
        JLabel titleLbl = new JLabel(titulo);
        titleLbl.setFont(new Font("Segoe UI", Font.BOLD, 10));
        titleLbl.setForeground(Color.GRAY);
        titleLbl.setHorizontalAlignment(SwingConstants.CENTER);
        valorLabel.setFont(FONT_METRIC);
        valorLabel.setForeground(cor);
        valorLabel.setHorizontalAlignment(SwingConstants.CENTER);
        card.add(titleLbl, BorderLayout.NORTH);
        card.add(valorLabel, BorderLayout.CENTER);
        return card;
    }

    private static JPanel criarPainelControles() {
        JPanel p = new JPanel(new FlowLayout(FlowLayout.RIGHT, 15, 15));
        p.setBackground(Color.WHITE);
        p.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, Color.LIGHT_GRAY));

        JButton btnExemplos = criarBotao("📂  CARREGAR CENÁRIO", new Color(70, 70, 80));
        JPopupMenu menuExemplos = new JPopupMenu();
        
        // --- TESTES ORIGINAIS ---
        JMenuItem itemFlush = new JMenuItem("1. Flush (Branch Misprediction)");
        itemFlush.addActionListener(e -> carregarExemplo("FLUSH"));
        JMenuItem itemBolhas = new JMenuItem("2. Estresse (Bolhas/Stall)");
        itemBolhas.addActionListener(e -> carregarExemplo("BOLHAS"));
        JMenuItem itemRaw = new JMenuItem("3. Dependência RAW");
        itemRaw.addActionListener(e -> carregarExemplo("RAW"));
        
        // --- TESTES DOS ÁUDIOS ---
        menuExemplos.addSeparator();
        JMenuItem itemAudioRaw = new JMenuItem("4. Validação de Espera (RAW)");
        itemAudioRaw.addActionListener(e -> carregarExemplo("AUDIO_RAW"));
        
        JMenuItem itemAudioBne = new JMenuItem("5. Validação de Especulação & Flush");
        itemAudioBne.addActionListener(e -> carregarExemplo("AUDIO_BNE"));

        menuExemplos.add(itemFlush);
        menuExemplos.add(itemBolhas);
        menuExemplos.add(itemRaw);
        menuExemplos.add(itemAudioRaw);
        menuExemplos.add(itemAudioBne);

        btnExemplos.addActionListener(e -> menuExemplos.show(btnExemplos, 0, -menuExemplos.getPreferredSize().height));

        JButton btnReset = criarBotao("🔄  REINICIAR / APLICAR", new Color(100, 100, 100));
        JButton btnStep = criarBotao("▶  PRÓXIMO CICLO", COR_PRIMARY);
        
        btnReset.addActionListener(e -> setupExecucao());
        btnStep.addActionListener(e -> avancarCiclo());

        p.add(btnExemplos);
        p.add(Box.createHorizontalStrut(20));
        p.add(btnReset);
        p.add(btnStep);
        return p;
    }

    private static JButton criarBotao(String texto, Color bg) {
        JButton b = new JButton(texto);
        b.setFont(new Font("Segoe UI", Font.BOLD, 12));
        b.setBackground(bg);
        b.setForeground(Color.WHITE);
        b.setFocusPainted(false);
        b.setBorder(new EmptyBorder(10, 20, 10, 20));
        return b;
    }

    private static JPanel criarPainelInstrucoes() {
        String[] colunas = {"OP", "Destino / Alvo", "Fonte 1", "Fonte 2"};
        modeloInstrucoes = new DefaultTableModel(colunas, 15);
        tabelaInstrucoes = new JTable(modeloInstrucoes);
        estilizarTabela(tabelaInstrucoes);

        String[] ops = {"", "LD", "ADD", "MULT", "BNE"};
        String[] regs = {"", "F0", "F1", "F2", "F3", "F4", "F5"};
        tabelaInstrucoes.getColumnModel().getColumn(0).setCellEditor(new DefaultCellEditor(new JComboBox<>(ops)));
        tabelaInstrucoes.getColumnModel().getColumn(2).setCellEditor(new DefaultCellEditor(new JComboBox<>(regs)));
        tabelaInstrucoes.getColumnModel().getColumn(3).setCellEditor(new DefaultCellEditor(new JComboBox<>(regs)));

        JPanel p = new JPanel(new BorderLayout());
        p.setBorder(BorderFactory.createTitledBorder(null, "PROGRAMA (INSTRUÇÕES)", TitledBorder.DEFAULT_JUSTIFICATION, TitledBorder.DEFAULT_POSITION, FONT_TITLE, COR_PRIMARY));
        p.setBackground(Color.WHITE);
        p.add(new JScrollPane(tabelaInstrucoes), BorderLayout.CENTER);
        return p;
    }

    private static void configurarLayoutHardware(JPanel p) {
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.fill = GridBagConstraints.BOTH;

        gbc.gridx = 0; gbc.gridy = 0; gbc.weightx = 1.0; gbc.weighty = 0.0;
        p.add(criarPainelFP(), gbc);

        gbc.gridx = 0; gbc.gridy = 1; gbc.weightx = 1.0; gbc.weighty = 0.4;
        p.add(criarPainelRS(), gbc);

        gbc.gridx = 0; gbc.gridy = 2; gbc.weightx = 1.0; gbc.weighty = 0.6;
        p.add(criarPainelROB(), gbc);
    }

    private static JPanel criarPainelRS() {
        String[] col = {"RS", "Busy", "Op", "Vj", "Vk", "Qj", "Qk", "Rem"};
        modeloRS = new DefaultTableModel(col, 0);
        tabelaRS = new JTable(modeloRS);
        estilizarTabela(tabelaRS);
        
        tabelaRS.setDefaultRenderer(Object.class, new DefaultTableCellRenderer(){
            @Override
            public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
                Component c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
                Object busyObj = table.getValueAt(row, 1);
                String busy = (busyObj != null) ? busyObj.toString() : "";
                if (!isSelected) {
                    c.setBackground("YES".equals(busy) ? new Color(255, 250, 220) : Color.WHITE);
                    c.setForeground(Color.BLACK);
                }
                return c;
            }
        });

        JPanel p = new JPanel(new BorderLayout());
        p.setBorder(BorderFactory.createTitledBorder(null, "RESERVATION STATIONS", TitledBorder.DEFAULT_JUSTIFICATION, TitledBorder.DEFAULT_POSITION, FONT_TITLE, COR_PRIMARY));
        p.setBackground(Color.WHITE);
        p.add(new JScrollPane(tabelaRS), BorderLayout.CENTER);
        return p;
    }

    private static JPanel criarPainelROB() {
        String[] col = {"ID", "Busy", "Tipo", "Dest/Alvo", "Valor/Tomado", "Ready"};
        modeloROB = new DefaultTableModel(col, 0);
        tabelaROB = new JTable(modeloROB);
        estilizarTabela(tabelaROB);

        tabelaROB.setDefaultRenderer(Object.class, new DefaultTableCellRenderer(){
            @Override
            public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
                Component c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
                Object readyObj = table.getValueAt(row, 5);
                Object valorObj = table.getValueAt(row, 4);
                String ready = (readyObj != null) ? readyObj.toString() : "";
                String valor = (valorObj != null) ? valorObj.toString() : "";
                
                c.setBackground(Color.WHITE); c.setForeground(Color.BLACK);
                if ("SIM".equals(ready)) {
                    c.setBackground(new Color(235, 255, 235)); 
                    if ("TOMADO".equals(valor)) c.setForeground(Color.RED); 
                }
                if (isSelected) {
                    c.setBackground(table.getSelectionBackground());
                    c.setForeground(table.getSelectionForeground());
                }
                return c;
            }
        });

        JPanel p = new JPanel(new BorderLayout());
        p.setBorder(BorderFactory.createTitledBorder(null, "REORDER BUFFER (ROB)", TitledBorder.DEFAULT_JUSTIFICATION, TitledBorder.DEFAULT_POSITION, FONT_TITLE, COR_PRIMARY));
        p.setBackground(Color.WHITE);
        p.add(new JScrollPane(tabelaROB), BorderLayout.CENTER);
        return p;
    }

    private static JPanel criarPainelFP() {
        JPanel p = new JPanel(new GridLayout(1, 2, 10, 0));
        p.setBackground(COR_FUNDO);

        JPanel pFP = new JPanel(new GridLayout(3, 2, 5, 5));
        pFP.setBorder(BorderFactory.createTitledBorder(null, "FP REGISTERS", TitledBorder.DEFAULT_JUSTIFICATION, TitledBorder.DEFAULT_POSITION, FONT_TITLE, COR_PRIMARY));
        pFP.setBackground(Color.WHITE);
        String[] nomes = {"F0", "F1", "F2", "F3", "F4", "F5"};
        for (int i = 0; i < nomes.length; i++) {
            camposFP[i] = new JTextField();
            camposFP[i].setHorizontalAlignment(JTextField.CENTER);
            JPanel linha = new JPanel(new BorderLayout());
            linha.setBackground(Color.WHITE);
            linha.add(new JLabel(nomes[i] + ": "), BorderLayout.WEST);
            linha.add(camposFP[i], BorderLayout.CENTER);
            pFP.add(linha);
        }

        JPanel pLat = new JPanel(new GridLayout(3, 2, 5, 5));
        pLat.setBorder(BorderFactory.createTitledBorder(null, "LATÊNCIA (CLOCKS)", TitledBorder.DEFAULT_JUSTIFICATION, TitledBorder.DEFAULT_POSITION, FONT_TITLE, COR_PRIMARY));
        pLat.setBackground(Color.WHITE);
        pLat.add(new JLabel("LOAD/STORE:")); campoLatLD = new JTextField("2"); pLat.add(campoLatLD);
        pLat.add(new JLabel("ADD/SUB:")); campoLatADD = new JTextField("4"); pLat.add(campoLatADD);
        pLat.add(new JLabel("MULT/DIV:")); campoLatMULT = new JTextField("10"); pLat.add(campoLatMULT);
        campoLatLD.setHorizontalAlignment(JTextField.CENTER); campoLatADD.setHorizontalAlignment(JTextField.CENTER); campoLatMULT.setHorizontalAlignment(JTextField.CENTER);

        p.add(pFP); p.add(pLat);
        return p;
    }

    private static void estilizarTabela(JTable t) {
        t.setRowHeight(25);
        t.setFont(FONT_NORMAL);
        t.getTableHeader().setFont(new Font("Segoe UI", Font.BOLD, 12));
        t.getTableHeader().setBackground(COR_HEADER_TABLE);
        t.setGridColor(new Color(230,230,230));
        t.setShowVerticalLines(false);
    }

    // ==================================================================================
    //                                  LÓGICA DO SIMULADOR
    // ==================================================================================

    private static void avancarCiclo() {
        cicloAtual++;
        realizarCommit();
        boolean emitiu = emitirUmaInstrucao();
        if (!emitiu && instrucoes != null && indiceProxInstrucao < instrucoes.size()) {
            ciclosDeBolha++;
        }
        executarOperacoes();
        escreverResultados();
        atualizarUI();
    }

    private static void realizarCommit() {
        if (itensNoRob == 0) return;
        EntradaROB head = rob[robHead];
        if (head.busy && head.ready) {
            if (head.ehDesvio) {
                if (head.desvioTomado) {
                    JOptionPane.showMessageDialog(null, "Misprediction Detectado! Executando Flush.\nPC corrigido para linha: " + head.enderecoAlvo, "Especulação de Desvio", JOptionPane.WARNING_MESSAGE);
                    realizarFlush(head.enderecoAlvo);
                    return;
                } else instrucoesCommitadas++;
            } else {
                int idxDest = indiceRegistrador(head.dest);
                if (idxDest >= 0) {
                    fp[idxDest] = head.valor;
                    String tagEsperada = "ROB" + head.id;
                    if (tagEsperada.equals(produtorRegistro[idxDest])) produtorRegistro[idxDest] = null;
                    atualizarCamposFP();
                }
                instrucoesCommitadas++;
            }
            head.limpar();
            robHead = (robHead + 1) % TAM_ROB;
            itensNoRob--;
        }
    }

    private static void realizarFlush(int novoPC) {
        for (RS r : loadStations) r.limpar();
        for (RS r : addStations)  r.limpar();
        for (RS r : multStations) r.limpar();
        for (EntradaROB r : rob) r.limpar();
        robHead = 0; robTail = 0; itensNoRob = 0;
        for (int i = 0; i < produtorRegistro.length; i++) produtorRegistro[i] = null;
        
        if (novoPC >= 0 && novoPC <= instrucoes.size()) indiceProxInstrucao = novoPC;
        else indiceProxInstrucao = instrucoes.size();
        
        atualizarUI();
    }

    private static boolean emitirUmaInstrucao() {
        if (instrucoes == null || instrucoes.isEmpty()) return false;
        if (indiceProxInstrucao >= instrucoes.size()) return false;
        if (itensNoRob >= TAM_ROB) return false;

        Instrucao inst = instrucoes.get(indiceProxInstrucao);
        String op = inst.op;
        RS[] alvo;
        if (op.equals("LD")) alvo = loadStations;
        else if (op.equals("ADD")) alvo = addStations;
        else if (op.equals("MULT")) alvo = multStations;
        else if (op.equals("BNE")) alvo = addStations;
        else { indiceProxInstrucao++; return true; }

        RS livre = null;
        for (RS rs : alvo) if (!rs.busy) { livre = rs; break; }
        if (livre == null) return false;

        int robIdVisual = rob[robTail].id;
        EntradaROB entrada = rob[robTail];
        entrada.busy = true; entrada.tipo = op; entrada.dest = inst.dest; entrada.ready = false; entrada.valor = 0f;
        
        if (op.equals("BNE")) {
            entrada.ehDesvio = true;
            try { entrada.enderecoAlvo = Integer.parseInt(inst.dest); } catch(Exception e) { entrada.enderecoAlvo = -1; }
        } else entrada.ehDesvio = false;

        livre.busy = true; livre.op = op; livre.destRobId = robTail;

        int idx1 = indiceRegistrador(inst.src1);
        if (idx1 >= 0) {
            if (produtorRegistro[idx1] == null) { livre.Vj = fp[idx1]; livre.Qj = null; }
            else { livre.Vj = null; livre.Qj = produtorRegistro[idx1]; }
        } else { livre.Vj = null; livre.Qj = null; }

        int idx2 = indiceRegistrador(inst.src2);
        if (idx2 >= 0) {
            if (produtorRegistro[idx2] == null) { livre.Vk = fp[idx2]; livre.Qk = null; }
            else { livre.Vk = null; livre.Qk = produtorRegistro[idx2]; }
        } else { livre.Vk = null; livre.Qk = null; }

        if (!op.equals("BNE")) {
            int idxDest = indiceRegistrador(inst.dest);
            if (idxDest >= 0) produtorRegistro[idxDest] = "ROB" + robIdVisual;
        }

        livre.remaining = 0;
        robTail = (robTail + 1) % TAM_ROB;
        itensNoRob++;
        indiceProxInstrucao++;
        return true;
    }

    private static void escreverResultados() {
        RS[] todas = unirTodasRS();
        for (RS rs : todas) {
            if (rs == null || !rs.busy || !rs.prontoParaWrite) continue;
            float resultado = 0f; boolean condicaoBNE = false;
            if ("LD".equals(rs.op)) resultado = (rs.Vj != null) ? rs.Vj : 0f;
            else if ("ADD".equals(rs.op)) {
                float vj = (rs.Vj != null) ? rs.Vj : 0f; float vk = (rs.Vk != null) ? rs.Vk : 0f;
                resultado = vj + vk;
            } else if ("MULT".equals(rs.op)) {
                float vj = (rs.Vj != null) ? rs.Vj : 0f; float vk = (rs.Vk != null) ? rs.Vk : 0f;
                resultado = vj * vk;
            } else if ("BNE".equals(rs.op)) {
                float vj = (rs.Vj != null) ? rs.Vj : 0f; float vk = (rs.Vk != null) ? rs.Vk : 0f;
                condicaoBNE = (Math.abs(vj - vk) > 0.0001);
            }

            if (rs.destRobId >= 0 && rs.destRobId < TAM_ROB) {
                EntradaROB r = rob[rs.destRobId];
                r.ready = true;
                if (r.ehDesvio) r.desvioTomado = condicaoBNE;
                else r.valor = resultado;
            }

            if (!"BNE".equals(rs.op)) {
                int idVisual = rob[rs.destRobId].id; String tagCDB = "ROB" + idVisual;
                for (RS r2 : todas) {
                    if (r2 == null || !r2.busy) continue;
                    if (tagCDB.equals(r2.Qj)) { r2.Vj = resultado; r2.Qj = null; }
                    if (tagCDB.equals(r2.Qk)) { r2.Vk = resultado; r2.Qk = null; }
                }
            }
            rs.limpar();
        }
    }

    private static void executarOperacoes() {
        for (RS rs : loadStations) processarExecucao(rs, latLD);
        for (RS rs : addStations)  processarExecucao(rs, latADD);
        for (RS rs : multStations) processarExecucao(rs, latMULT);
    }

    private static void processarExecucao(RS rs, int latencia) {
        if (rs == null || !rs.busy) return; // PROTEÇÃO CONTRA NULL
        if (rs.remaining == 0 && rs.Qj == null && rs.Qk == null && !rs.prontoParaWrite) rs.remaining = latencia;
        if (rs.remaining > 0) { rs.remaining--; if (rs.remaining == 0) rs.prontoParaWrite = true; }
    }

    // --- CARREGAMENTO E RESET ---

    private static void carregarExemplo(String tipo) {
        DefaultTableModel m = (DefaultTableModel) tabelaInstrucoes.getModel();
        for(int i=0; i<m.getRowCount(); i++) for(int j=0; j<4; j++) m.setValueAt("", i, j);
        
        campoLatLD.setText("2"); campoLatADD.setText("4"); campoLatMULT.setText("10");
        Random r = new Random();
        for (JTextField f : camposFP) f.setText(String.valueOf(1 + r.nextInt(20)));

        if (tipo.equals("FLUSH")) {
            m.setValueAt("LD",   0, 0); m.setValueAt("F1", 0, 1); m.setValueAt("F0", 0, 2);
            m.setValueAt("LD",   1, 0); m.setValueAt("F2", 1, 1); m.setValueAt("F0", 1, 2);
            m.setValueAt("BNE",  2, 0); m.setValueAt("0",  2, 1); m.setValueAt("F1", 2, 2); m.setValueAt("F2", 2, 3);
            m.setValueAt("ADD",  3, 0); m.setValueAt("F3", 3, 1); m.setValueAt("F1", 3, 2); m.setValueAt("F2", 3, 3);
            camposFP[1].setText("10.0"); camposFP[2].setText("20.0");
        } 
        else if (tipo.equals("BOLHAS")) {
            m.setValueAt("MULT", 0, 0); m.setValueAt("F0", 0, 1); m.setValueAt("F1", 0, 2); m.setValueAt("F2", 0, 3);
            for(int i=1; i<=11; i++) {
                m.setValueAt("ADD", i, 0); m.setValueAt("F3", i, 1); m.setValueAt("F1", i, 2); m.setValueAt("F2", i, 3);
            }
            campoLatMULT.setText("15"); campoLatADD.setText("1");
        } 
        else if (tipo.equals("RAW")) {
            m.setValueAt("LD",   0, 0); m.setValueAt("F1", 0, 1); m.setValueAt("F0", 0, 2);
            m.setValueAt("ADD",  1, 0); m.setValueAt("F2", 1, 1); m.setValueAt("F1", 1, 2); m.setValueAt("F0", 1, 3);
            m.setValueAt("MULT", 2, 0); m.setValueAt("F3", 2, 1); m.setValueAt("F2", 2, 2); m.setValueAt("F0", 2, 3);
            m.setValueAt("ADD",  3, 0); m.setValueAt("F4", 3, 1); m.setValueAt("F3", 3, 2); m.setValueAt("F0", 3, 3);
        }
        else if (tipo.equals("AUDIO_RAW")) {
            m.setValueAt("ADD",  0, 0); m.setValueAt("F3", 0, 1); m.setValueAt("F1", 0, 2); m.setValueAt("F2", 0, 3);
            m.setValueAt("MULT", 1, 0); m.setValueAt("F4", 1, 1); m.setValueAt("F3", 1, 2); m.setValueAt("F5", 1, 3);
            campoLatADD.setText("10"); campoLatMULT.setText("2");
        }
        else if (tipo.equals("AUDIO_BNE")) {
            m.setValueAt("BNE",  0, 0); m.setValueAt("3",  0, 1); m.setValueAt("F1", 0, 2); m.setValueAt("F2", 0, 3);
            m.setValueAt("MULT", 1, 0); m.setValueAt("F4", 1, 1); m.setValueAt("F4", 1, 2); m.setValueAt("F4", 1, 3);
            m.setValueAt("MULT", 2, 0); m.setValueAt("F5", 2, 1); m.setValueAt("F5", 2, 2); m.setValueAt("F5", 2, 3);
            m.setValueAt("ADD",  3, 0); m.setValueAt("F0", 3, 1); m.setValueAt("F0", 3, 2); m.setValueAt("F0", 3, 3);
            camposFP[1].setText("10.0"); camposFP[2].setText("20.0");
            campoLatADD.setText("10"); campoLatMULT.setText("2"); 
        }
    }

    private static void setupExecucao() {
        fp = new float[camposFP.length];
        for (int i = 0; i < camposFP.length; i++) {
            String texto = camposFP[i].getText().trim();
            if (!texto.isEmpty()) fp[i] = Float.parseFloat(texto);
        }
        DefaultTableModel m = (DefaultTableModel) tabelaInstrucoes.getModel();
        instrucoes = new ArrayList<>();
        for (int i = 0; i < m.getRowCount(); i++) {
            String op = (String) m.getValueAt(i, 0); String dest = (String) m.getValueAt(i, 1);
            String f1 = (String) m.getValueAt(i, 2); String f2 = (String) m.getValueAt(i, 3);
            if (op != null && !op.isBlank()) instrucoes.add(new Instrucao(op, dest, f1, f2));
        }
        latLD = Integer.parseInt(campoLatLD.getText().trim());
        latADD = Integer.parseInt(campoLatADD.getText().trim());
        latMULT = Integer.parseInt(campoLatMULT.getText().trim());
        
        cicloAtual = 0; indiceProxInstrucao = 0; instrucoesCommitadas = 0; ciclosDeBolha = 0;
        
        inicializarRS(); inicializarROB();
        produtorRegistro = new String[fp.length];
        for (int i = 0; i < produtorRegistro.length; i++) produtorRegistro[i] = null;
        atualizarUI();
    }

    private static void configurarInstrucoesIniciais() {
        carregarExemplo("FLUSH");
    }

    private static void atualizarUI() {
        atualizarTabelaRS(); atualizarTabelaROB(); atualizarMetricasUI();
    }

    private static void atualizarMetricasUI() {
        lblCicloVal.setText(String.valueOf(cicloAtual));
        lblBolhasVal.setText(String.valueOf(ciclosDeBolha));
        float ipc = (cicloAtual > 0) ? (float) instrucoesCommitadas / cicloAtual : 0f;
        lblIPCVal.setText(String.format("%.2f", ipc));
    }

    // --- UTILS ---
    private static void inicializarRS() {
        for (int i = 0; i < loadStations.length; i++) loadStations[i] = new RS("Load" + (i+1));
        for (int i = 0; i < addStations.length; i++)  addStations[i]  = new RS("Add" + (i+1));
        for (int i = 0; i < multStations.length; i++) multStations[i] = new RS("Mult" + (i+1));
    }
    private static void inicializarROB() {
        rob = new EntradaROB[TAM_ROB];
        for (int i = 0; i < TAM_ROB; i++) rob[i] = new EntradaROB(i + 1);
        robHead = 0; robTail = 0; itensNoRob = 0;
    }
    private static RS[] unirTodasRS() {
        RS[] todas = new RS[loadStations.length + addStations.length + multStations.length];
        int k = 0;
        for(RS r : loadStations) todas[k++] = r; for(RS r : addStations) todas[k++] = r; for(RS r : multStations) todas[k++] = r;
        return todas;
    }
    private static void atualizarTabelaRS() {
        if (modeloRS == null) return;
        modeloRS.setRowCount(0);
        RS[] todas = unirTodasRS();
        for (RS r : todas) {
            if (r == null) continue;
            modeloRS.addRow(new Object[]{r.name, r.busy?"YES":"NO", r.op!=null?r.op:"", r.Vj!=null?r.Vj:"", r.Vk!=null?r.Vk:"", r.Qj!=null?r.Qj:"", r.Qk!=null?r.Qk:"", r.remaining});
        }
    }
    private static void atualizarTabelaROB() {
        if (modeloROB == null) return;
        modeloROB.setRowCount(0);
        for (EntradaROB r : rob) {
            modeloROB.addRow(new Object[]{r.id, r.busy?"YES":"NO", r.tipo, r.dest, r.ehDesvio?(r.ready?(r.desvioTomado?"TOMADO":"NO"):""):(r.ready?r.valor:""), r.ready?"YES":"NO"});
        }
    }
    private static void atualizarCamposFP() {
        if (fp == null) return;
        for (int i = 0; i < fp.length; i++) camposFP[i].setText(String.valueOf(fp[i]));
    }
    private static int indiceRegistrador(String reg) {
        if (reg == null || !reg.startsWith("F")) return -1;
        try { return Integer.parseInt(reg.substring(1)); } catch (Exception e) { return -1; }
    }
}