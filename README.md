# Simulador Didático do Algoritmo de Tomasulo

## Integrantes
    - Edson Pimenta de Almeida
    - Túlio Gomes Braga
    - Luís Augusto Starling Toledo

## Descrição do Projeto

Este projeto consiste em um simulador didático do algoritmo de Tomasulo, utilizando instruções MIPS como referência para a simulação. O objetivo é auxiliar estudantes no entendimento de arquiteturas superescalares e execução fora de ordem por meio do acompanhamento visual dos estágios do pipeline, gerenciamento de dependências e técnicas de especulação de desvios.

## Funcionalidades

- Simulação do algoritmo de Tomasulo para instruções MIPS, incluindo buffer de reordenamento e suporte à especulação de instruções de desvio condicional.
- Interface gráfica interativa para visualização dos estágios de despacho, execução e commit das instruções.
- Execução passo a passo, facilitando o acompanhamento do comportamento do pipeline e das dependências entre instruções em tempo real.
- Apresentação de métricas como IPC (Instruções por Ciclo), ciclos totais de execução, ciclos de bolha (stalls) e outras relevantes para análise de desempenho de processadores superescalares.

## Público-alvo

O simulador é voltado para estudantes e professores de Arquitetura de Computadores e áreas afins, oferecendo um ambiente didático para experimentação e aprofundamento do funcionamento do algoritmo de Tomasulo e dos principais conceitos sobre pipelines superescalares.

## Observações

- Flexível para futuras expansões, como adicionar novas métricas ou configurar unidades funcionais.
- Ferramenta visual e interativa que conecta teoria e prática, promovendo aprendizado ativo sobre execução dinâmica de instruções.

