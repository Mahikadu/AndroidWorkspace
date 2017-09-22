package com.autodetectimage.util;

/**
 * Moving Average algorithm
 * Two version: simple and weighted
 * Created by Denis on 02.09.2016.
 */
public class MovingAverage {

	/**
	 * Data vector size
	 */
	private final int dimension;

	/**
	 * Average length
	 */
	private final int length;

	/**
	 * Data storage: (vector dimension) * (average length)
	 */
	private final float [] storage;

	/**
	 * Intermediate result for data sum
	 */
	private final float [] total;

	/**
	 * Weighted data sum
	 */
	private float [] weightedTotal;
	/**
	 * Current average count
	 */
	private int count;

	/**
	 * Last operation result. Used for duplicate.
	 */
	private boolean valid;

	public MovingAverage(int vectorSize, int slidingLength) {
		this.dimension = vectorSize;
		this.length = slidingLength;

		this.storage = new float[this.dimension * this.length];

		this.total = new float[this.dimension];
		this.weightedTotal = new float[this.dimension];
	}

	public void append(float [] vector) {
		// Check vector array
		if (vector != null && vector.length != dimension) {
			throw new IllegalArgumentException(String.format("Invalid vector length %d to average %d",
					vector.length, total.length));
		}

		if (vector == null) {
			remove();
		} else {
			if (count == length) {
				remove();
			}

			final int offset = count * dimension;
			++count;
			for (int i = 0; i < dimension; ++i) {
				storage[offset + i] = vector[i];
				total[i] += vector[i];
				weightedTotal[i] += vector[i] * count;
			}
			valid = true;
		}
	}

	public void remove() {
		if (count > 0) {
			// Subtract oldest values from total
			for (int i = 0; i < dimension; ++i) {
				weightedTotal[i] -= total[i];
				total[i] -= storage[i];
			}

			// Remove from storage
			--count;
			System.arraycopy(storage, dimension, storage, 0, count* dimension);
			valid = false;
		}
	}

	public void duplicate() {
		if (valid) {
			float [] vector = new float[dimension];
			System.arraycopy(storage, (count-1)* dimension, vector, 0, dimension);
			append(vector);
		} else {
			remove();
		}
	}

	public void reset() {
		count = 0;
		valid = false;
	}

	public float [] average(boolean weighted) {
		if (count == 0) {
			// No total
			return null;
		} else {
			float [] avg = new float[dimension];
			if (weighted) {
				final float scale = 2.f / (count * (count+1));
				for (int i = 0; i < dimension; ++i) {
					avg[i] += weightedTotal[i] * scale;
				}
			} else {
				// Simple Moving
				final float scale = 1f / count;
				for (int i = 0; i < dimension; ++i) {
					avg[i] = total[i] * scale;
				}
			}
			return avg;
		}
	}

	public int fullness(int scale) {
		return scale * count / length;
	}

	public float fullness() {
		return (float) count / (float) length;
	}
}
