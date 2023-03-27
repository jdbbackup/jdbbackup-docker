package com.fathzer.jdbbackup.utils;

import java.util.function.Supplier;

public class Cache<V> {
	private volatile V value;
	
	public Cache() {
		this.value = null;
	}
	
	public V get(Supplier<V> supplier) {
		if (this.value==null) {
			synchronized(this) {
				if (value==null) {
					this.value = supplier.get();
				}
			}
		}
		return this.value;
	}
	
	public synchronized void set(V value) {
		this.value = value;
	}
}
