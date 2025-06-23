package br.ufscar.dc.compiladores;

import br.ufscar.dc.compiladores.JanderParser.*;
import br.ufscar.dc.compiladores.SymbolTable.JanderType;
import java.util.List;
import java.util.stream.Collectors;
import org.antlr.v4.runtime.tree.ParseTree;
import java.util.ArrayList;

public class JanderGeradorC extends JanderBaseVisitor<Void> {

    private final StringBuilder output;
    private final JanderSemantico semantico;
    private final SymbolTable symbolTable;

    public JanderGeradorC(JanderSemantico semantico) {
        this.output = new StringBuilder();
        this.semantico = semantico;
        this.symbolTable = semantico.getSymbolTable();
    }

    public String getOutput() {
        return output.toString();
    }

    private String getCType(JanderType janderType) {
        switch (janderType) {
            case INTEGER: return "int";
            case REAL: return "float";
            case LITERAL: return "char*";
            case LOGICAL: return "bool";
            default: return "void";
        }
    }

    private String getFullCTypeName(Tipo_estendidoContext ctx) {
        String baseType;
        if (ctx.tipo_basico_ident().tipo_basico() != null) {
            JanderType janderType;
            switch (ctx.tipo_basico_ident().tipo_basico().getText().toLowerCase()) {
                case "inteiro": janderType = JanderType.INTEGER; break;
                case "real": janderType = JanderType.REAL; break;
                case "literal": janderType = JanderType.LITERAL; break;
                case "logico": janderType = JanderType.LOGICAL; break;
                default: janderType = JanderType.INVALID; break;
            }
            baseType = getCType(janderType);
        } else {
            String customTypeName = ctx.tipo_basico_ident().IDENT().getText();
            JanderType customJanderType = symbolTable.getSymbolType(customTypeName);
            if (customJanderType == JanderType.RECORD || customJanderType == JanderType.POINTER) {
                baseType = customTypeName;
            } else if (customJanderType != JanderType.INVALID) {
                baseType = getCType(customJanderType);
            }
            else {
                baseType = "void";
            }
        }

        if (ctx.getText().startsWith("^")) {
            return baseType + "*";
        }
        return baseType;
    }

    private String visitAndCapture(ParseTree ctx) {
        JanderGeradorC tempVisitor = new JanderGeradorC(this.semantico);
        tempVisitor.visit(ctx);
        return tempVisitor.getOutput().trim();
    }

    @Override
    public Void visitPrograma(ProgramaContext ctx) {
        output.append("#include <stdio.h>\n");
        output.append("#include <stdlib.h>\n");
        output.append("#include <stdbool.h>\n");
        output.append("#include <string.h>\n\n");

        if(ctx.declaracoes() != null) {
            for (Decl_local_globalContext decl : ctx.declaracoes().decl_local_global()) {
                if (decl.declaracao_local() != null && decl.declaracao_local().TIPO() != null) {
                    visitDeclaracao_local(decl.declaracao_local());
                }
            }
            
            for (Decl_local_globalContext decl : ctx.declaracoes().decl_local_global()) {
                if (decl.declaracao_local() != null && decl.declaracao_local().CONSTANTE() != null) {
                    visitDeclaracao_constante_global(decl.declaracao_local());
                }
            }

            for (Decl_local_globalContext decl : ctx.declaracoes().decl_local_global()) {
                if (decl.declaracao_global() != null) {
                    generateFunctionPrototype(decl.declaracao_global());
                }
            }
            
            for (Decl_local_globalContext decl : ctx.declaracoes().decl_local_global()) {
                if (decl.declaracao_global() != null) {
                    visitDeclaracao_global(decl.declaracao_global());
                }
            }
        }
        
        output.append("int main() {\n");
        if (ctx.corpo() != null) {
            visitCorpo(ctx.corpo());
        }
        output.append("    return 0;\n");
        output.append("}\n");
        return null;
    }
    
    public Void visitDeclaracao_constante_global(Declaracao_localContext ctx) {
        if (ctx.CONSTANTE() != null) {
            JanderType constJanderType;
            switch (ctx.tipo_basico().getText().toLowerCase()) {
                case "inteiro": constJanderType = JanderType.INTEGER; break;
                case "real": constJanderType = JanderType.REAL; break;
                case "literal": constJanderType = JanderType.LITERAL; break;
                case "logico": constJanderType = JanderType.LOGICAL; break;
                default: constJanderType = JanderType.INVALID; break;
            }
            String constType = getCType(constJanderType);
            output.append("const ").append(constType).append(" ").append(ctx.IDENT().getText());
            output.append(" = ").append(ctx.valor_constante().getText()).append(";\n");
        }
        return null;
    }

    private void generateFunctionPrototype(Declaracao_globalContext ctx) {
        String funcName = ctx.IDENT().getText();
        String returnType;

        if (ctx.FUNCAO() != null) {
            returnType = getFullCTypeName(ctx.tipo_estendido());
        } else {
            returnType = "void";
        }

        output.append(returnType).append(" ").append(funcName).append("(");
        if (ctx.parametros() != null) {
            visitParametros(ctx.parametros());
        }
        output.append(");\n\n");
    }

    private JanderType getJanderTypeFromTipoEstendido(Tipo_estendidoContext ctx) {
        if (ctx.tipo_basico_ident().tipo_basico() != null) {
            switch (ctx.tipo_basico_ident().tipo_basico().getText().toLowerCase()) {
                case "inteiro": return JanderType.INTEGER;
                case "real": return JanderType.REAL;
                case "literal": return JanderType.LITERAL;
                case "logico": return JanderType.LOGICAL;
                default: return JanderType.INVALID;
            }
        } else {
            String typeName = ctx.tipo_basico_ident().IDENT().getText();
            return symbolTable.getSymbolType(typeName);
        }
    }

    @Override
    public Void visitDeclaracao_global(Declaracao_globalContext ctx) {
        String funcName = ctx.IDENT().getText();
        String returnType;

        if (ctx.FUNCAO() != null) {
            returnType = getFullCTypeName(ctx.tipo_estendido());
        } else {
            returnType = "void";
        }

        output.append(returnType).append(" ").append(funcName).append("(");
        if (ctx.parametros() != null) {
            visitParametros(ctx.parametros());
        }
        output.append(") {\n");

        symbolTable.openScope();

        if (ctx.parametros() != null) {
            for (ParametroContext paramCtx : ctx.parametros().parametro()) {
                JanderType paramType = getJanderTypeFromTipoEstendido(paramCtx.tipo_estendido());
                for (IdentificadorContext ident : paramCtx.identificador()) {
                    symbolTable.addSymbol(ident.getText(), paramType);
                }
            }
        }
        
        for (Declaracao_localContext decl : ctx.declaracao_local()) {
            visitDeclaracao_local(decl);
        }

        for (CmdContext cmd : ctx.cmd()) {
            visit(cmd);
        }

        symbolTable.closeScope();

        output.append("}\n\n");
        return null;
    }

    @Override
    public Void visitParametros(ParametrosContext ctx) {
        List<String> paramsStr = ctx.parametro().stream()
            .map(p -> visitAndCapture(p))
            .collect(Collectors.toList());
        output.append(String.join(", ", paramsStr));
        return null;
    }

    @Override
    public Void visitParametro(ParametroContext ctx) {
        String baseTypeName = getFullCTypeName(ctx.tipo_estendido());

        final String finalTypeName = (ctx.VAR() != null && !baseTypeName.endsWith("*"))
                ? baseTypeName + "*"
                : baseTypeName;

        List<String> idents = ctx.identificador().stream()
                .map(id -> finalTypeName + " " + id.getText())
                .collect(Collectors.toList());
        output.append(String.join(", ", idents));
        return null;
    }

    @Override
    public Void visitDeclaracao_local(Declaracao_localContext ctx) {
        if (ctx.TIPO() != null) {
            String newTypeName = ctx.IDENT().getText();
            output.append("    typedef struct {\n");
            for (VariavelContext memberCtx : ctx.tipo().registro().variavel()) {
                String memberTypeName = getFullCTypeName(memberCtx.tipo().tipo_estendido());
                boolean isLiteral = memberTypeName.equals("char*");
                String finalMemberType = isLiteral ? "char" : memberTypeName;

                for(IdentificadorContext ident : memberCtx.identificador()){
                    output.append("        ").append(finalMemberType).append(" ").append(ident.getText());
                    if(isLiteral) {
                        output.append("[100]");
                    }
                    output.append(";\n");
                }
            }
            output.append("    } ").append(newTypeName).append(";\n");

        } else if (ctx.DECLARE() != null) {
            visitVariavel(ctx.variavel());
        } else if (ctx.CONSTANTE() != null) {
            JanderType constJanderType;
            switch (ctx.tipo_basico().getText().toLowerCase()) {
                case "inteiro": constJanderType = JanderType.INTEGER; break;
                case "real": constJanderType = JanderType.REAL; break;
                case "literal": constJanderType = JanderType.LITERAL; break;
                case "logico": constJanderType = JanderType.LOGICAL; break;
                default: constJanderType = JanderType.INVALID; break;
            }
            String constType = getCType(constJanderType);
            output.append("const ").append(constType).append(" ").append(ctx.IDENT().getText());
            output.append(" = ").append(ctx.valor_constante().getText()).append(";\n");
        }
        return null;
    }

    @Override
    public Void visitVariavel(VariavelContext ctx) {
        if (ctx.tipo().registro() != null) {
            List<String> structVarNames = ctx.identificador().stream()
                                              .map(ident -> ident.getText())
                                              .collect(Collectors.toList());

            output.append("    struct {\n");

            for (VariavelContext memberCtx : ctx.tipo().registro().variavel()) {
                output.append("        ");

                String memberTypeName = getFullCTypeName(memberCtx.tipo().tipo_estendido());
                boolean isMemberLiteral = memberTypeName.equals("char*");
                String finalMemberType = isMemberLiteral ? "char" : memberTypeName;

                List<String> memberIdents = memberCtx.identificador().stream()
                    .map(ident -> {
                        String idStr = ident.getText();
                        return isMemberLiteral ? idStr + "[100]" : idStr;
                    })
                    .collect(Collectors.toList());
                
                output.append("        ").append(finalMemberType).append(" ").append(String.join(", ", memberIdents)).append(";\n");
            }
            output.append("    } ").append(String.join(", ", structVarNames)).append(";\n");
            return null;

        } else {
            String typeName = getFullCTypeName(ctx.tipo().tipo_estendido());
            boolean isLiteral = typeName.equals("char*");

            List<String> idents = ctx.identificador().stream()
                .map(ident -> {
                    String idStr = ident.getText();
                    if (isLiteral) {
                        return idStr + "[100]";
                    }
                    return idStr;
                })
                .collect(Collectors.toList());


            String finalTypeName = isLiteral ? "char" : typeName;

            output.append("    ").append(finalTypeName).append(" ").append(String.join(", ", idents)).append(";\n");
        }
        
        return null;
    }

    @Override
    public Void visitCmdLeia(CmdLeiaContext ctx) {
        for (int i = 0; i < ctx.identificador().size(); i++) {
            IdentificadorContext ident = ctx.identificador(i);
            String varName = ident.getText();
            JanderType varType = symbolTable.getSymbolType(varName);

            if (varType == JanderType.LITERAL) {
                output.append("    gets(").append(varName).append(");\n");
            } else {
                String formatSpecifier = "";
                String argument = "";

                switch (varType) {
                    case INTEGER:
                        formatSpecifier = "%d";
                        argument = "&" + varName;
                        break;
                    case REAL:
                        formatSpecifier = "%f";
                        argument = "&" + varName;
                        break;
                    default:
                        formatSpecifier = "%d";
                        argument = "&" + varName;
                        break;
                }
                output.append("    scanf(\"").append(formatSpecifier).append("\", ").append(argument).append(");\n");
            }
        }
        return null;
    }

    @Override
    public Void visitCmdEscreva(CmdEscrevaContext ctx) {
        StringBuilder formatString = new StringBuilder();
        List<String> arguments = new ArrayList<>();

        for (ExpressaoContext expr : ctx.expressao()) {
            String capturedExpr = visitAndCapture(expr);

            if (capturedExpr.startsWith("\"") && capturedExpr.endsWith("\"")) {
                String literalContent = capturedExpr.substring(1, capturedExpr.length() - 1);
                formatString.append(literalContent.replace("%", "%%"));
            } else {
                JanderType exprType = JanderSemanticoUtils.checkType(symbolTable, expr);
                switch (exprType) {
                    case INTEGER:
                        formatString.append("%d");
                        arguments.add(capturedExpr);
                        break;
                    case REAL:
                        formatString.append("%f");
                        arguments.add(capturedExpr);
                        break;
                    case LITERAL:
                        formatString.append("%s");
                        arguments.add(capturedExpr);
                        break;
                    case LOGICAL:
                        formatString.append("%s");
                        arguments.add(capturedExpr + " ? \"verdadeiro\" : \"falso\"");
                        break;
                    default:
                        formatString.append("%s");
                        arguments.add("\"<ERRO_TIPO>\"");
                        break;
                }
            }
        }

        output.append("    printf(\"").append(formatString.toString()).append("\"");
        if (!arguments.isEmpty()) {
            output.append(", ").append(String.join(", ", arguments));
        }
        output.append(");\n");

        return null;
    }

    @Override
    public Void visitCmdSe(CmdSeContext ctx) {
        output.append("    if (").append(visitAndCapture(ctx.expressao())).append(") {\n");

        List<CmdContext> ifCmds;
        List<CmdContext> elseCmds = new ArrayList<>();

        if (ctx.SENAO() != null) {
            int senaoTokenIndex = ctx.SENAO().getSymbol().getTokenIndex();
            
            ifCmds = ctx.cmd().stream()
                         .filter(c -> c.getStart().getTokenIndex() < senaoTokenIndex)
                         .collect(Collectors.toList());
            
            elseCmds = ctx.cmd().stream()
                          .filter(c -> c.getStart().getTokenIndex() > senaoTokenIndex)
                          .collect(Collectors.toList());
        } else {
            ifCmds = ctx.cmd();
        }

        ifCmds.forEach(this::visit);
        output.append("    }\n");

        if (ctx.SENAO() != null) {
            output.append("    else {\n");
            elseCmds.forEach(this::visit);
            output.append("    }\n");
        }
        return null;
    }

    @Override
    public Void visitCmdCaso(CmdCasoContext ctx) {
        output.append("    switch (").append(visitAndCapture(ctx.exp_aritmetica())).append(") {\n");
        
        for (Item_selecaoContext item : ctx.selecao().item_selecao()) {
            for (Numero_intervaloContext ni : item.constantes().numero_intervalo()) {
                int start = Integer.parseInt(ni.NUM_INT(0).getText());
                int end = start;
                if(ni.NUM_INT().size() > 1) {
                    end = Integer.parseInt(ni.NUM_INT(1).getText());
                }
                if (ni.op_unario(0) != null) start = -start;
                if (ni.op_unario().size() > 1) end = -end;

                for (int i = start; i <= end; i++) {
                    output.append("        case ").append(i).append(":\n");
                }
            }
            item.cmd().forEach(this::visit);
            output.append("            break;\n");
        }
        
        if (ctx.SENAO() != null) {
            output.append("        default:\n");
            ctx.cmd().forEach(this::visit);
            output.append("            break;\n");
        }
        
        output.append("    }\n");
        return null;
    }

    @Override
    public Void visitCmdPara(CmdParaContext ctx) {
        String ident = ctx.IDENT().getText();
        String start = visitAndCapture(ctx.exp_aritmetica(0));
        String end = visitAndCapture(ctx.exp_aritmetica(1));
        output.append("    for (").append(ident).append(" = ").append(start).append("; ").append(ident).append(" <= ").append(end).append("; ").append(ident).append("++) {\n");
        ctx.cmd().forEach(this::visit);
        output.append("    }\n");
        return null;
    }

    @Override
    public Void visitCmdEnquanto(CmdEnquantoContext ctx) {
        output.append("    while (").append(visitAndCapture(ctx.expressao())).append(") {\n");
        ctx.cmd().forEach(this::visit);
        output.append("    }\n");
        return null;
    }

    @Override
    public Void visitCmdFaca(CmdFacaContext ctx) {
        output.append("    do {\n");
        for (CmdContext cmd : ctx.cmd()) {
            visit(cmd);
        }

        ExpressaoContext expressaoJander = ctx.expressao();
        String whileCondition;
        Fator_logicoContext fator = expressaoJander.termo_logico(0).fator_logico(0);
        boolean temNao = fator.getChildCount() > 1 && fator.getChild(0).getText().equals("nao");
        
        if (temNao) {
            String innerExpression = visitAndCapture(fator.parcela_logica());
            whileCondition = "!(" + innerExpression + ")";
        } else {
            whileCondition = visitAndCapture(expressaoJander);
        }
        
        output.append("    } while (").append(whileCondition).append(");\n");
        return null;
    }

    @Override
    public Void visitCmdAtribuicao(CmdAtribuicaoContext ctx) {
        String lhs = ctx.identificador().getText();
        String rhs = visitAndCapture(ctx.expressao());

        JanderParser.ExpressaoContext lhsExpr = new JanderParser.ExpressaoContext(ctx.identificador().getParent(), 0);
        lhsExpr.children = new ArrayList<>();
        lhsExpr.children.add(ctx.identificador());
        
        JanderType lhsType = semantico.resolveIdentificadorType(ctx.identificador(), symbolTable, new StringBuilder());
        
        if (lhsType == JanderType.LITERAL && rhs.startsWith("\"")) {
            output.append("    strcpy(").append(lhs).append(", ").append(rhs).append(");\n");
        } else {
            output.append("    ");
            
            if (ctx.getChild(0).getText().equals("^")) {
                output.append("*");
            }
            
            output.append(lhs).append(" = ").append(rhs).append(";\n");
        }
        return null;
    }

    @Override
    public Void visitCmdChamada(CmdChamadaContext ctx) {
        List<String> args = ctx.expressao().stream()
            .map(expr -> visitAndCapture(expr))
            .collect(Collectors.toList());
        output.append("    ").append(ctx.IDENT().getText()).append("(").append(String.join(", ", args)).append(");\n");
        return null;
    }

    @Override
    public Void visitCmdRetorne(CmdRetorneContext ctx) {
        output.append("    return ").append(visitAndCapture(ctx.expressao())).append(";\n");
        return null;
    }

    @Override
    public Void visitExpressao(ExpressaoContext ctx) {
        output.append(visitAndCapture(ctx.termo_logico(0)));
        for (int i = 0; i < ctx.op_logico_1().size(); i++) {
            output.append(" || ");
            output.append(visitAndCapture(ctx.termo_logico(i + 1)));
        }
        return null;
    }

    @Override
    public Void visitTermo_logico(Termo_logicoContext ctx) {
        output.append(visitAndCapture(ctx.fator_logico(0)));
        for (int i = 0; i < ctx.op_logico_2().size(); i++) {
            output.append(" && ");
            output.append(visitAndCapture(ctx.fator_logico(i + 1)));
        }
        return null;
    }

    @Override
    public Void visitFator_logico(Fator_logicoContext ctx) {
        if (ctx.getText().startsWith("nao")) {
            output.append("!(");
        }
        output.append(visitAndCapture(ctx.parcela_logica()));
        if (ctx.getText().startsWith("nao")) {
            output.append(")");
        }
        return null;
    }

    @Override
    public Void visitParcela_logica(Parcela_logicaContext ctx) {
        if (ctx.VERDADEIRO() != null) output.append("true");
        else if (ctx.FALSO() != null) output.append("false");
        else visit(ctx.exp_relacional());
        return null;
    }

    @Override
    public Void visitExp_relacional(Exp_relacionalContext ctx) {
        String exp1 = visitAndCapture(ctx.exp_aritmetica(0));

        if (ctx.op_relacional() == null) {
            output.append(exp1);
            return null;
        }

        String exp2 = visitAndCapture(ctx.exp_aritmetica(1));
        String op = ctx.op_relacional().getText();
        
        JanderType t1 = JanderSemanticoUtils.checkType(symbolTable, ctx.exp_aritmetica(0));
        
        String finalOp = op;
        if (finalOp.equals("=")) finalOp = "==";
        else if (finalOp.equals("<>")) finalOp = "!=";
        
        if (t1 == JanderType.LITERAL) {
            output.append("strcmp(").append(exp1).append(", ").append(exp2).append(") ").append(finalOp).append(" 0");
        } else {
            output.append(exp1).append(" ").append(finalOp).append(" ").append(exp2);
        }
        
        return null;
    }

    @Override
    public Void visitExp_aritmetica(Exp_aritmeticaContext ctx) {
        output.append(visitAndCapture(ctx.termo(0)));
        for (int i = 0; i < ctx.op1().size(); i++) {
            output.append(" ").append(ctx.op1(i).getText()).append(" ");
            output.append(visitAndCapture(ctx.termo(i + 1)));
        }
        return null;
    }

    @Override
    public Void visitTermo(TermoContext ctx) {
        output.append(visitAndCapture(ctx.fator(0)));
        for (int i = 0; i < ctx.op2().size(); i++) {
            output.append(" ").append(ctx.op2(i).getText()).append(" ");
            output.append(visitAndCapture(ctx.fator(i + 1)));
        }
        return null;
    }

    @Override
    public Void visitFator(FatorContext ctx) {
        output.append(visitAndCapture(ctx.parcela(0)));
        for (int i = 0; i < ctx.op3().size(); i++) {
            output.append(" % ");
            output.append(visitAndCapture(ctx.parcela(i + 1)));
        }
        return null;
    }

    @Override
    public Void visitParcela(ParcelaContext ctx) {
        if (ctx.op_unario() != null) {
            output.append(ctx.op_unario().getText());
        }
        if (ctx.parcela_unario() != null) {
            visitParcela_unario(ctx.parcela_unario());
        } else {
            visitParcela_nao_unario(ctx.parcela_nao_unario());
        }
        return null;
    }

    @Override
    public Void visitParcela_unario(Parcela_unarioContext ctx) {
        if (ctx.getText().startsWith("^")) {
            output.append("*");
        }
        if (ctx.identificador() != null) {
            output.append(ctx.identificador().getText());
        } else if (ctx.IDENT() != null) {
            output.append(ctx.IDENT().getText()).append("(");
            List<String> args = ctx.expressao().stream()
                                   .map(expr -> visitAndCapture(expr))
                                   .collect(Collectors.toList());
            output.append(String.join(", ", args));
            output.append(")");
        } else if (ctx.NUM_INT() != null) {
            output.append(ctx.NUM_INT().getText());
        } else if (ctx.NUM_REAL() != null) {
            output.append(ctx.NUM_REAL().getText());
        } else if (ctx.expressao() != null && !ctx.expressao().isEmpty()) {
            output.append("(").append(visitAndCapture(ctx.expressao(0))).append(")");
        }
        return null;
    }

    @Override
    public Void visitParcela_nao_unario(Parcela_nao_unarioContext ctx) {
        if (ctx.getText().startsWith("&")) {
            output.append("&");
        }
        if (ctx.identificador() != null) {
            output.append(ctx.identificador().getText());
        } else {
            output.append(ctx.CADEIA().getText());
        }
        return null;
    }
}