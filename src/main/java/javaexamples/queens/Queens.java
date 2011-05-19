package javaexamples.queens;

import java.util.ArrayList;
import java.util.List;

import cspom.CSPOM;
import cspom.variable.CSPOMVariable;

public final class Queens {

	private static final int SIZE = 8;

	private Queens() {
	}

	public static CSPOM queens(final int nbQueens) {

		final CSPOM problem = new CSPOM();

		final List<CSPOMVariable> queens = new ArrayList<CSPOMVariable>(
				nbQueens);

		for (int i = 0; i < nbQueens; i++) {
			queens.add(problem.interVar("q" + i, 0, nbQueens));
		}

		for (int i = nbQueens; --i >= 0;) {
			for (int j = i; --j >= 0;) {
				problem.ctr("neq(" + queens.get(i) + " , " + queens.get(j)
						+ ")");
				problem.ctr("neq(abs(sub(" + queens.get(i) + ","
						+ queens.get(j) + ")), " + Math.abs(i - j) + ")");
			}
		}

		return problem;
	}

	public static void main(final String[] args) {
		System.out.println(queens(SIZE).toGML());
	}
}