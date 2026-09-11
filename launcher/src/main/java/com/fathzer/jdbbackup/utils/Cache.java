package com.fathzer.jdbbackup.utils;

import java.util.function.Supplier;

/** A double-checked locking Cache that can contains one object
 * @param <V> The class of the object contained by the Cache.
 * Please note that only the access to the object is secured by the double check.
 * This means that if you use this class to store a mutable object, the object should be thread safe if you plan to let multiple threads use it.
 */
public class Cache<V> {
	private volatile V value;
	
	/** Constructor.
	 * <br>By default, the cache is empty
	 */
	public Cache() {
		this.value = null;
	}
	
	/** Gets the value in the cache.
	 * @param supplier a supplier that will be called to get the object if cache is empty.
	 * It is guaranteed that this method is called only when cache is empty and is will be called only one time.
	 * @return a V instance
	 */
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
	
	/** Set the object in this cache.
	 * @param value The new value
	 */
	public synchronized void set(V value) {
		this.value = value;
	}
}
