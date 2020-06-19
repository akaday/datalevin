(ns datalevin.bits
  (:require [clojure.java.io :as io]
            [taoensso.nippy :as nippy])
  (:import [java.io File]
           [java.nio ByteBuffer]))

(defn delete-files
  "Recursively delete "
  [& fs]
  (when-let [f (first fs)]
    (if-let [cs (seq (.listFiles (io/file f)))]
      (recur (concat cs fs))
      (do (io/delete-file f)
          (recur (rest fs))))))

(defn file
  "Return path as File, create it if missing"
  [path]
  (let [^File f (io/file path)]
    (if (.exists f)
      f
      (do (.mkdir f)
          f))))

(def ^:const buffer-overflow "BufferOverflow:")

(defn- check-buffer-overflow
  [^long length ^long remaining]
  (assert (<= length remaining)
          (str buffer-overflow
               " trying to put "
               length
               " bytes while "
               remaining
               "remaining in the ByteBuffer.")))

(defn put-long
  [^ByteBuffer bb n]
  (assert (integer? n) "put-long requires an integer")
  (check-buffer-overflow Long/BYTES (.remaining bb))
  (.putLong bb ^long (long n)))

(defn put-bytes
  [^ByteBuffer bb ^bytes bs]
  (let [len (alength bs)]
    (assert (< 0 len) "Cannot put empty byte array into ByteBuffer")
    (check-buffer-overflow len (.remaining bb))
    (.put bb bs)))

(defn put-byte
  [^ByteBuffer bb b]
  (assert (instance? Byte b) "put-byte requires a byte")
  (check-buffer-overflow 1 (.remaining bb))
  (.put bb ^byte b))

(defn put-data
  [^ByteBuffer bb x]
  (put-bytes bb (nippy/fast-freeze x)))

(defn measure-size
  "measure size of x in number of bytes"
  [x]
  (cond
    (bytes? x)         (alength ^bytes x)
    (integer? x)       8
    (instance? Byte x) 1
    :else              (alength ^bytes (nippy/fast-freeze x))))

(defn get-long
  "Get a long from a ByteBuffer"
  [^ByteBuffer bb]
  (.getLong bb))

(defn get-byte
  "Get a byte from a ByteBuffer"
  [^ByteBuffer bb]
  (.get bb))

(defn get-bytes
  "Copy content from a ByteBuffer to a byte array, useful for
   e.g. read txn result, as buffer content is gone when txn is done"
  [^ByteBuffer bb]
  (let [n   (.remaining bb)
        arr (byte-array n)]
    (.get bb arr)
    arr))

(defn get-data
  "Read data from a ByteBuffer"
  [^ByteBuffer bb]
  (when-let [bs (get-bytes bb)]
    (nippy/fast-thaw bs)))

(defn long-buffer
  "Create a ByteBuffer to hold a long value."
  ([]
   (ByteBuffer/allocateDirect Long/BYTES))
  ([v]
   (let [^ByteBuffer bb (long-buffer)]
     (put-long bb v)
     (.flip bb))))

(defn bytes-buffer
  "Create a ByteBuffer to hold a byte array."
  [^bytes bs]
  (let [^ByteBuffer bb (ByteBuffer/allocateDirect (alength bs))]
    (put-bytes bb bs)
    (.flip bb)))

(defn data-buffer
  "Create a ByteBuffer to hold a piece of Clojure data."
  [data]
  (bytes-buffer (nippy/fast-freeze data)))