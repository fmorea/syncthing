package com.fmorea.syncthing.chess;

import java.util.ArrayList;
import java.util.Arrays;

/**
 * GameLogic handles the core chess engine rules and evaluation.
 * Includes support for Castling, En Passant, Pawn Promotion and Positional Evaluation.
 */
public class GameLogic {
    private final String[][] matrix;
    private Movement mov = new Movement(0, 0, 0, 0);
    private boolean toccaAlBianco = true;
    private final boolean lockTurn = false;
    private final String promotionB = "donB";
    private final String promotionN = "donN";
    private ArrayList<Movement> legalMoves;
    private final ArrayList<String[][]> history = new ArrayList<>();
    private final ArrayList<String[][]> redoHistory = new ArrayList<>();

    // State for special moves
    private boolean whiteKingMoved = false;
    private boolean whiteRookAMoved = false;
    private boolean whiteRookHMoved = false;
    private boolean blackKingMoved = false;
    private boolean blackRookAMoved = false;
    private boolean blackRookHMoved = false;
    private int enPassantCol = -1;

    // History of special states for undo
    private final ArrayList<SpecialState> stateHistory = new ArrayList<>();
    private final ArrayList<SpecialState> redoStateHistory = new ArrayList<>();

    private static class SpecialState {
        boolean wKM, wRAM, wRHM, bKM, bRAM, bRHM;
        int epCol;
        SpecialState(boolean wkm, boolean wram, boolean wrhm, boolean bkm, boolean bram, boolean brhm, int ep) {
            this.wKM = wkm; this.wRAM = wram; this.wRHM = wrhm;
            this.bKM = bkm; this.bRAM = bram; this.bRHM = brhm;
            this.epCol = ep;
        }
    }

    // --- Piece Evaluation Constants ---
    private static final int PAWN_VAL = 100;
    private static final int KNIGHT_VAL = 320;
    private static final int BISHOP_VAL = 330;
    private static final int ROOK_VAL = 500;
    private static final int QUEEN_VAL = 900;
    private static final int KING_VAL = 20000;

    private static final int[] PAWN_PST = {
        0,  0,  0,  0,  0,  0,  0,  0,
        50, 50, 50, 50, 50, 50, 50, 50,
        10, 10, 20, 30, 30, 20, 10, 10,
         5,  5, 10, 25, 25, 10,  5,  5,
         0,  0,  0, 20, 20,  0,  0,  0,
         5, -5,-10,  0,  0,-10, -5,  5,
         5, 10, 10,-20,-20, 10, 10,  5,
         0,  0,  0,  0,  0,  0,  0,  0
    };

    private static final int[] KNIGHT_PST = {
        -50,-40,-30,-30,-30,-30,-40,-50,
        -40,-20,  0,  0,  0,  0,-20,-40,
        -30,  0, 10, 15, 15, 10,  0,-30,
        -30,  5, 15, 20, 20, 15,  5,-30,
        -30,  0, 15, 20, 20, 15,  0,-30,
        -30,  5, 10, 15, 15, 10,  5,-30,
        -40,-20,  0,  5,  5,  0,-20,-40,
        -50,-40,-30,-30,-30,-30,-40,-50
    };

    private static final int[] BISHOP_PST = {
        -20,-10,-10,-10,-10,-10,-10,-20,
        -10,  0,  0,  0,  0,  0,  0,-10,
        -10,  0,  5, 10, 10,  5,  0,-10,
        -10,  5,  5, 10, 10,  5,  5,-10,
        -10,  0, 10, 10, 10, 10,  0,-10,
        -10, 10, 10, 10, 10, 10, 10,-10,
        -10,  5,  0,  0,  0,  0,  5,-10,
        -20,-10,-10,-10,-10,-10,-10,-20
    };

    private static final int[] ROOK_PST = {
          0,  0,  0,  0,  0,  0,  0,  0,
          5, 10, 10, 10, 10, 10, 10,  5,
         -5,  0,  0,  0,  0,  0,  0, -5,
         -5,  0,  0,  0,  0,  0,  0, -5,
         -5,  0,  0,  0,  0,  0,  0, -5,
         -5,  0,  0,  0,  0,  0,  0, -5,
         -5,  0,  0,  0,  0,  0,  0, -5,
          0,  0,  0,  5,  5,  0,  0,  0
    };

    private static final int[] QUEEN_PST = {
        -20,-10,-10, -5, -5,-10,-10,-20,
        -10,  0,  0,  0,  0,  0,  0,-10,
        -10,  0,  5,  5,  5,  5,  0,-10,
         -5,  0,  5,  5,  5,  5,  0, -5,
          0,  0,  5,  5,  5,  5,  0, -5,
        -10,  5,  5,  5,  5,  5,  0,-10,
        -10,  0,  5,  0,  0,  0,  0,-10,
        -20,-10,-10, -5, -5,-10,-10,-20
    };

    private static final int[] KING_PST = {
        -30,-40,-40,-50,-50,-40,-40,-30,
        -30,-40,-40,-50,-50,-40,-40,-30,
        -30,-40,-40,-50,-50,-40,-40,-30,
        -30,-40,-40,-50,-50,-40,-40,-30,
        -20,-30,-30,-40,-40,-30,-30,-20,
        -10,-20,-20,-20,-20,-20,-20,-10,
         20, 20,  0,  0,  0,  0, 20, 20,
         20, 30, 10,  0,  0, 10, 30, 20
    };

    private static final int[] KING_ENDGAME_PST = {
        -50,-40,-30,-20,-20,-30,-40,-50,
        -30,-20,-10,  0,  0,-10,-20,-30,
        -30,-10, 20, 30, 30, 20,-10,-30,
        -30,-10, 30, 40, 40, 30,-10,-30,
        -30,-10, 30, 40, 40, 30,-10,-30,
        -30,-10, 20, 30, 30, 20,-10,-30,
        -30,-30,  0,  0,  0,  0,-30,-30,
        -50,-30,-30,-30,-30,-30,-30,-50
    };

    public GameLogic() {
        this.matrix = new String[8][8];
    }

    public void createStandardChessboard() {
        for (int i = 0; i < 8; i++) Arrays.fill(matrix[i], null);
        for (int x = 1; x <= 8; x++) setPezzo(2, x, "pedB");
        setPezzo(1, 1, "torB"); setPezzo(1, 8, "torB");
        setPezzo(1, 2, "cavB"); setPezzo(1, 7, "cavB");
        setPezzo(1, 3, "alfB"); setPezzo(1, 6, "alfB");
        setPezzo(1, 4, "donB"); setPezzo(1, 5, "re_B");
        for (int x = 1; x <= 8; x++) setPezzo(7, x, "pedN");
        setPezzo(8, 1, "torN"); setPezzo(8, 8, "torN");
        setPezzo(8, 2, "cavN"); setPezzo(8, 7, "cavN");
        setPezzo(8, 3, "alfN"); setPezzo(8, 6, "alfN");
        setPezzo(8, 4, "donN"); setPezzo(8, 5, "re_N");

        this.toccaAlBianco = true;
        this.whiteKingMoved = this.whiteRookAMoved = this.whiteRookHMoved = false;
        this.blackKingMoved = this.blackRookAMoved = this.blackRookHMoved = false;
        this.enPassantCol = -1;
        this.history.clear();
        this.redoHistory.clear();
        this.stateHistory.clear();
        this.redoStateHistory.clear();
        updateLegalMoves();
    }

    public void updateLegalMoves() {
        ArrayList<Movement> pseudo = calculateAllPseudoLegalMoves();
        this.legalMoves = filterLegalMoves(pseudo);
    }

    public boolean isInCheck() {
        return isKingAttacked(toccaAlBianco ? 'B' : 'N');
    }

    private boolean isKingAttacked(char color) {
        int ky = -1, kx = -1;
        for (int y = 1; y <= 8; y++) {
            for (int x = 1; x <= 8; x++) {
                if (getTipoPezzo(y, x) == 'r' && getColorePezzo(y, x) == color) {
                    ky = y; kx = x; break;
                }
            }
            if (ky != -1) break;
        }
        return isSquareAttacked(ky, kx, color == 'B' ? 'N' : 'B');
    }

    private boolean isSquareAttacked(int ty, int tx, char attackerColor) {
        if (ty == -1) return false;
        int[][] knightMoves = {{2,1},{2,-1},{-2,1},{-2,-1},{1,2},{1,-2},{-1,2},{-1,-2}};
        for (int[] m : knightMoves) {
            int y = ty + m[0], x = tx + m[1];
            if (isInsideChessBoard(y, x) && getTipoPezzo(y, x) == 'c' && getColorePezzo(y, x) == attackerColor) return true;
        }
        int pDir = (attackerColor == 'B') ? -1 : 1;
        int[] pxAttacks = {tx - 1, tx + 1};
        for (int x : pxAttacks) {
            int y = ty + pDir;
            if (isInsideChessBoard(y, x) && getTipoPezzo(y, x) == 'p' && getColorePezzo(y, x) == attackerColor) return true;
        }
        int[][] dirs = {{1,0},{-1,0},{0,1},{0,-1},{1,1},{1,-1},{-1,1},{-1,-1}};
        for (int i = 0; i < 8; i++) {
            int y = ty + dirs[i][0], x = tx + dirs[i][1];
            if (isInsideChessBoard(y, x) && getTipoPezzo(y, x) == 'r' && getColorePezzo(y, x) == attackerColor) return true;
            while (isInsideChessBoard(y, x)) {
                String p = getPezzo(y, x);
                if (p != null) {
                    if (p.charAt(3) == attackerColor) {
                        char type = p.charAt(0);
                        if (i < 4 && (type == 't' || type == 'd')) return true;
                        if (i >= 4 && (type == 'a' || type == 'd')) return true;
                    }
                    break;
                }
                y += dirs[i][0]; x += dirs[i][1];
            }
        }
        return false;
    }

    private ArrayList<Movement> filterLegalMoves(ArrayList<Movement> pseudo) {
        ArrayList<Movement> legal = new ArrayList<>();
        char myColor = toccaAlBianco ? 'B' : 'N';
        for (Movement m : pseudo) {
            if (getTipoPezzo(m.getY0(), m.getX0()) == 'r' && Math.abs(m.getX() - m.getX0()) == 2) {
                if (isSquareAttacked(m.getY0(), m.getX0(), myColor == 'B' ? 'N' : 'B')) continue;
                int stepX = (m.getX() > m.getX0()) ? 1 : -1;
                if (isSquareAttacked(m.getY0(), m.getX0() + stepX, myColor == 'B' ? 'N' : 'B')) continue;
            }
            
            // Explicitly identify en passant for unmaking to avoid heuristic corruption
            String p = getPezzo(m.getY0(), m.getX0());
            boolean isEP = (p != null && p.charAt(0) == 'p' && m.getX() != m.getX0() && isEmpty(m.getY(), m.getX()));
            
            String captured = makeTemporaryMove(m);
            if (!isKingAttacked(myColor)) legal.add(m);
            unmakeTemporaryMove(m, captured, isEP);
        }
        return legal;
    }

    private String makeTemporaryMove(Movement m) {
        String p = getPezzo(m.getY0(), m.getX0());
        String captured = getPezzo(m.getY(), m.getX());
        if (p.charAt(0) == 'p' && m.getX() != m.getX0() && captured == null) {
            captured = getPezzo(m.getY0(), m.getX());
            setPezzo(m.getY0(), m.getX(), null);
        }
        setPezzo(m.getY0(), m.getX0(), null);
        setPezzo(m.getY(), m.getX(), p);
        return captured;
    }

    private void unmakeTemporaryMove(Movement m, String captured, boolean isEP) {
        String p = getPezzo(m.getY(), m.getX());
        setPezzo(m.getY0(), m.getX0(), p);
        setPezzo(m.getY(), m.getX(), isEP ? null : captured);
        if (isEP) setPezzo(m.getY0(), m.getX(), captured);
    }

    private ArrayList<Movement> calculateAllPseudoLegalMoves() {
        ArrayList<Movement> moves = new ArrayList<>();
        char myColor = toccaAlBianco ? 'B' : 'N';
        for (int y = 1; y <= 8; y++) {
            for (int x = 1; x <= 8; x++) {
                String p = getPezzo(y, x);
                if (p != null && p.charAt(3) == myColor) {
                    addPseudoMoves(y, x, moves);
                }
            }
        }
        return moves;
    }

    private void addPseudoMoves(int y0, int x0, ArrayList<Movement> moves) {
        String p = getPezzo(y0, x0);
        char type = p.charAt(0);
        char color = p.charAt(3);
        switch (type) {
            case 'p':
                int dir = (color == 'B') ? 1 : -1;
                if (isInsideChessBoard(y0 + dir, x0) && isEmpty(y0 + dir, x0)) {
                    moves.add(new Movement(y0, x0, y0 + dir, x0));
                    if ((color == 'B' && y0 == 2) || (color == 'N' && y0 == 7)) {
                        if (isEmpty(y0 + 2 * dir, x0)) moves.add(new Movement(y0, x0, y0 + 2 * dir, x0));
                    }
                }
                for (int dx : new int[]{-1, 1}) {
                    int tx = x0 + dx; int ty = y0 + dir;
                    if (isInsideChessBoard(ty, tx)) {
                        String target = getPezzo(ty, tx);
                        if (target != null && target.charAt(3) != color) moves.add(new Movement(y0, x0, ty, tx));
                        if (target == null && tx == enPassantCol && ((color == 'B' && y0 == 5) || (color == 'N' && y0 == 4))) {
                            moves.add(new Movement(y0, x0, ty, tx));
                        }
                    }
                }
                break;
            case 'c':
                int[][] cMoves = {{2,1},{2,-1},{-2,1},{-2,-1},{1,2},{1,-2},{-1,2},{-1,-2}};
                for (int[] m : cMoves) addIfValid(y0, x0, y0 + m[0], x0 + m[1], moves);
                break;
            case 't':
                addSliding(y0, x0, new int[][]{{1,0},{-1,0},{0,1},{0,-1}}, moves);
                break;
            case 'a':
                addSliding(y0, x0, new int[][]{{1,1},{1,-1},{-1,1},{-1,-1}}, moves);
                break;
            case 'd':
                addSliding(y0, x0, new int[][]{{1,0},{-1,0},{0,1},{0,-1},{1,1},{1,-1},{-1,1},{-1,-1}}, moves);
                break;
            case 'r':
                for (int dy = -1; dy <= 1; dy++) {
                    for (int dx = -1; dx <= 1; dx++) {
                        if (dy != 0 || dx != 0) addIfValid(y0, x0, y0 + dy, x0 + dx, moves);
                    }
                }
                if (color == 'B' && !whiteKingMoved) {
                    if (!whiteRookHMoved && "torB".equals(getPezzo(1,8)) && isEmpty(1, 6) && isEmpty(1, 7)) moves.add(new Movement(1, 5, 1, 7));
                    if (!whiteRookAMoved && "torB".equals(getPezzo(1,1)) && isEmpty(1, 2) && isEmpty(1, 3) && isEmpty(1, 4)) moves.add(new Movement(1, 5, 1, 3));
                } else if (color == 'N' && !blackKingMoved) {
                    if (!blackRookHMoved && "torN".equals(getPezzo(8,8)) && isEmpty(8, 6) && isEmpty(8, 7)) moves.add(new Movement(8, 5, 8, 7));
                    if (!blackRookAMoved && "torN".equals(getPezzo(8,1)) && isEmpty(8, 2) && isEmpty(8, 3) && isEmpty(8, 4)) moves.add(new Movement(8, 5, 8, 3));
                }
                break;
        }
    }

    private void addIfValid(int y0, int x0, int y, int x, ArrayList<Movement> moves) {
        if (isInsideChessBoard(y, x)) {
            String p = getPezzo(y, x);
            if (p == null || p.charAt(3) != getColorePezzo(y0, x0)) moves.add(new Movement(y0, x0, y, x));
        }
    }

    private void addSliding(int y0, int x0, int[][] dirs, ArrayList<Movement> moves) {
        char color = getColorePezzo(y0, x0);
        for (int[] d : dirs) {
            int y = y0 + d[0], x = x0 + d[1];
            while (isInsideChessBoard(y, x)) {
                String p = getPezzo(y, x);
                if (p == null) { moves.add(new Movement(y0, x0, y, x)); }
                else { if (p.charAt(3) != color) moves.add(new Movement(y0, x0, y, x)); break; }
                y += d[0]; x += d[1];
            }
        }
    }

    public boolean isPromotion(int y0, int x0, int y, int x) {
        String p = getPezzo(y0, x0);
        if (p == null || p.charAt(0) != 'p') return false;
        return (p.charAt(3) == 'B' && y == 8) || (p.charAt(3) == 'N' && y == 1);
    }

    public boolean move(int y0, int x0, int y, int x, String promoPiece) {
        Movement m = new Movement(y0, x0, y, x);
        if (legalMoves.contains(m)) {
            history.add(copy(matrix));
            stateHistory.add(new SpecialState(whiteKingMoved, whiteRookAMoved, whiteRookHMoved, blackKingMoved, blackRookAMoved, blackRookHMoved, enPassantCol));
            redoHistory.clear();
            redoStateHistory.clear();
            executeMove(m, promoPiece);
            if (!lockTurn) toccaAlBianco = !toccaAlBianco;
            updateLegalMoves();
            mov = m;
            return true;
        }
        return false;
    }

    private void executeMove(Movement m, String promoPiece) {
        String p = getPezzo(m.getY0(), m.getX0());
        char type = p.charAt(0); char color = p.charAt(3);
        enPassantCol = -1;
        if (type == 'p') {
            if (Math.abs(m.getY() - m.getY0()) == 2) enPassantCol = m.getX0();
            if (m.getX() != m.getX0() && isEmpty(m.getY(), m.getX())) { setPezzo(m.getY0(), m.getX(), null); }
            if ((color == 'B' && m.getY() == 8) || (color == 'N' && m.getY() == 1)) {
                p = (promoPiece != null) ? promoPiece : (color == 'B' ? promotionB : promotionN);
            }
        }
        if (type == 'r') {
            if (color == 'B') whiteKingMoved = true; else blackKingMoved = true;
            if (Math.abs(m.getX() - m.getX0()) == 2) {
                if (m.getX() == 7) { setPezzo(m.getY(), 8, null); setPezzo(m.getY(), 6, color == 'B' ? "torB" : "torN"); }
                else if (m.getX() == 3) { setPezzo(m.getY(), 1, null); setPezzo(m.getY(), 4, color == 'B' ? "torB" : "torN"); }
            }
        }
        if (type == 't') {
            if (color == 'B') { if (m.getY0() == 1 && m.getX0() == 1) whiteRookAMoved = true; if (m.getY0() == 1 && m.getX0() == 8) whiteRookHMoved = true; }
            else { if (m.getY0() == 8 && m.getX0() == 1) blackRookAMoved = true; if (m.getY0() == 8 && m.getX0() == 8) blackRookHMoved = true; }
        }
        setPezzo(m.getY0(), m.getX0(), null);
        setPezzo(m.getY(), m.getX(), p);
    }

    public void undo() {
        if (!history.isEmpty()) {
            redoHistory.add(copy(matrix));
            redoStateHistory.add(new SpecialState(whiteKingMoved, whiteRookAMoved, whiteRookHMoved, blackKingMoved, blackRookAMoved, blackRookHMoved, enPassantCol));
            
            String[][] h = history.remove(history.size() - 1);
            for(int i=0; i<8; i++) System.arraycopy(h[i], 0, matrix[i], 0, 8);
            
            SpecialState s = stateHistory.remove(stateHistory.size() - 1);
            whiteKingMoved = s.wKM; whiteRookAMoved = s.wRAM; whiteRookHMoved = s.wRHM;
            blackKingMoved = s.bKM; blackRookAMoved = s.bRAM; blackRookHMoved = s.bRHM;
            enPassantCol = s.epCol;
            
            toccaAlBianco = !toccaAlBianco;
            updateLegalMoves();
        }
    }

    public void redo() {
        if (!redoHistory.isEmpty()) {
            history.add(copy(matrix));
            stateHistory.add(new SpecialState(whiteKingMoved, whiteRookAMoved, whiteRookHMoved, blackKingMoved, blackRookAMoved, blackRookHMoved, enPassantCol));

            String[][] h = redoHistory.remove(redoHistory.size() - 1);
            for(int i=0; i<8; i++) System.arraycopy(h[i], 0, matrix[i], 0, 8);
            
            SpecialState s = redoStateHistory.remove(redoStateHistory.size() - 1);
            whiteKingMoved = s.wKM; whiteRookAMoved = s.wRAM; whiteRookHMoved = s.wRHM;
            blackKingMoved = s.bKM; blackRookAMoved = s.bRAM; blackRookHMoved = s.bRHM;
            enPassantCol = s.epCol;
            
            toccaAlBianco = !toccaAlBianco;
            updateLegalMoves();
        }
    }

    public int objectiveFunction() {
        int score = 0; int whiteBishops = 0, blackBishops = 0; int totalMaterial = 0;
        int[] wPawnsFile = new int[10], bPawnsFile = new int[10];
        for (int y = 1; y <= 8; y++) {
            for (int x = 1; x <= 8; x++) {
                String p = getPezzo(y, x); if (p == null) continue;
                char type = p.charAt(0); char color = p.charAt(3);
                if (color == 'B') { if (type == 'a') whiteBishops++; if (type == 'p') wPawnsFile[x]++; }
                else { if (type == 'a') blackBishops++; if (type == 'p') bPawnsFile[x]++; }
                if (type != 'r') totalMaterial += getPieceValue(type);
            }
        }
        boolean isEndgame = totalMaterial < 1500;
        for (int y = 1; y <= 8; y++) {
            for (int x = 1; x <= 8; x++) {
                String p = getPezzo(y, x); if (p == null) continue;
                char type = p.charAt(0); char color = p.charAt(3);
                int val = getPieceValue(type);
                int pstIdx = (color == 'B') ? (8 - y) * 8 + (x - 1) : (y - 1) * 8 + (x - 1);
                switch (type) {
                    case 'p': val += PAWN_PST[pstIdx] + evalPawn(x, color, wPawnsFile, bPawnsFile); break;
                    case 'c': val += KNIGHT_PST[pstIdx]; break;
                    case 'a': val += BISHOP_PST[pstIdx]; break;
                    case 't': val += ROOK_PST[pstIdx] + evalRook(x); break;
                    case 'd': val += QUEEN_PST[pstIdx]; break;
                    case 'r': val += (isEndgame ? KING_ENDGAME_PST[pstIdx] : KING_PST[pstIdx]); break;
                }
                score += (color == 'B' ? val : -val);
            }
        }
        if (whiteBishops >= 2) score += 50; if (blackBishops >= 2) score -= 50;
        return score;
    }

    private int getPieceValue(char type) {
        switch (type) {
            case 'p': return PAWN_VAL; case 'c': return KNIGHT_VAL; case 'a': return BISHOP_VAL;
            case 't': return ROOK_VAL; case 'd': return QUEEN_VAL; case 'r': return KING_VAL;
            default: return 0;
        }
    }

    private int evalPawn(int x, char color, int[] wFiles, int[] bFiles) {
        int bonus = 0; if (x < 1 || x > 8) return 0;
        if (color == 'B' && wFiles[x] > 1) bonus -= 15; if (color == 'N' && bFiles[x] > 1) bonus -= 15;
        boolean isolated = true;
        if (x > 1 && (color == 'B' ? wFiles[x-1] : bFiles[x-1]) > 0) isolated = false;
        if (x < 8 && (color == 'B' ? wFiles[x+1] : bFiles[x+1]) > 0) isolated = false;
        if (isolated) bonus -= 20;
        return bonus;
    }

    private int evalRook(int x) {
        for (int r = 1; r <= 8; r++) { String p = getPezzo(r, x); if (p != null && p.charAt(0) == 'p') return 0; }
        return 20;
    }

    private String lastSenderId = "";

    public String serializeBoard() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 8; i++) { for (int j = 0; j < 8; j++) sb.append(matrix[i][j] == null ? "null" : matrix[i][j]).append(","); }
        sb.append(toccaAlBianco ? "W" : "B").append(",");
        sb.append(lastSenderId);
        return sb.toString();
    }

    public void deserializeBoard(String data) {
        String[] parts = data.split(","); if (parts.length < 65) return;
        for (int i = 0; i < 64; i++) matrix[i / 8][i % 8] = parts[i].equals("null") ? null : parts[i];
        toccaAlBianco = "W".equals(parts[64]);
        if (parts.length > 65) lastSenderId = parts[65];
        history.clear(); stateHistory.clear(); redoHistory.clear(); redoStateHistory.clear();
        whiteKingMoved = whiteRookAMoved = whiteRookHMoved = blackKingMoved = blackRookAMoved = blackRookHMoved = true;
        enPassantCol = -1; updateLegalMoves();
    }

    public String getPezzo(int y, int x) { return isInsideChessBoard(y, x) ? matrix[y - 1][x - 1] : null; }
    public void setPezzo(int y, int x, String p) { if (isInsideChessBoard(y, x)) matrix[y - 1][x - 1] = p; }
    public String getLastSenderId() { return lastSenderId; }
    public void setLastSenderId(String id) { this.lastSenderId = id; }
    public char getTipoPezzo(int y, int x) { String p = getPezzo(y, x); return p != null ? p.charAt(0) : '0'; }
    public char getColorePezzo(int y, int x) { String p = getPezzo(y, x); return p != null ? p.charAt(3) : '0'; }
    public boolean isEmpty(int y, int x) { return getPezzo(y, x) == null; }
    public boolean isInsideChessBoard(int y, int x) { return y >= 1 && y <= 8 && x >= 1 && x <= 8; }
    public ArrayList<Movement> getLegalMoves() { return legalMoves; }
    public Movement getMov() { return mov; }
    public boolean toccaAlBianco() { return toccaAlBianco; }
    private String[][] copy(String[][] s) { String[][] d = new String[8][8]; for (int i = 0; i < 8; i++) d[i] = Arrays.copyOf(s[i], 8); return d; }
}
