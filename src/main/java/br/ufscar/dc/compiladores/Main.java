package br.ufscar.dc.compiladores;

import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import br.ufscar.dc.compiladores.JanderParser.ProgramaContext;
import java.io.PrintWriter;

public class Main {
    public static void main(String[] args) {
        try {
            CharStream cs = CharStreams.fromFileName(args[0]);
            String arquivoSaida = args[1];
            PrintWriter pw = new PrintWriter(arquivoSaida, "UTF-8");

            JanderLexer lex = new JanderLexer(cs);
            CommonTokenStream tokens = new CommonTokenStream(lex);

            JanderParser parser = new JanderParser(tokens);
            MyCustomErrorListener mcel = new MyCustomErrorListener(pw);
            parser.removeErrorListeners();
            parser.addErrorListener(mcel);

            ProgramaContext arvore = parser.programa();

            if (!mcel.parada) {
                JanderSemantico semantico = new JanderSemantico(pw);
                semantico.visit(arvore);

                if (!semantico.hasErrors()) {
                    JanderGeradorC gerador = new JanderGeradorC(semantico);
                    gerador.visit(arvore);
                    pw.print(gerador.getOutput());
                } else {
                    semantico.printErrors();
                }
            }
            
            pw.close();

        } catch (Exception e) {
            System.err.println("Ocorreu um erro inesperado: " + e.getMessage());
            System.exit(1);
        }
    }
}