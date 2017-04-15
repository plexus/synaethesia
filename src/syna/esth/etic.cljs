(ns syna.esth.etic
  (:refer-clojure :exclude [delay *out*])
  (:require [clojure.string :as str]
            [syna.esth.proc :refer [cmd!]]
            [syna.esth.strm
             :refer [|
                     dbg
                     pass-through-stream
                     <file
                     >file
                     split-stream
                     stream-concat
                     truncate
                     sponge
                     buffer-size]]
            [syna.esth.sox :refer [sox dcshift delay echos bass treble bandreject fir]]))

(def *err* js/process.stderr)
(def *in* js/process.stdin)
(def *out* js/process.stdout)

(def sox-args
  (-> (sox {:channels 2 :depth 8})
      (fir 1 0.8)
      (treble 4)
      (echos 0.7 0.8 250 0.4)
      #_(dcshift 0.2)))


(defn -main [infile outfile]
  (let [in            (if infile (<file infile) *in*)
        img->bmp      (cmd! ["convert" "-" "bmp:-"])
        bmp->png      (cmd! ["convert" "bmp:-" "png:-"])
        sox           (cmd! sox-args)
        out           (if outfile (>file outfile) *out*)]

    (| (:err img->bmp) *err*)
    (| (:err bmp->png) *err*)
    (| (:err sox) *err*)

    (| in

       ;; Turn the image into an uncompressed bitmap, can't do much with it otherwise
       img->bmp

       ;; Soak up the whole input, because we need to know the size of the BMP image
       (sponge (fn [bmp]
                 (let [bmp-size (buffer-size bmp)

                       ;; chop off the header, the header needs to stay intact,
                       ;; with the rest you can mess as much as you like
                       [head body] (split-stream bmp 1000)]

                   ;; pipe the rest of the image to sox, this is where the *magic* happens
                   (| body sox)

                   ;; now add the head and processed body together again
                   (| (stream-concat head sox)
                      ;; The size might have changed so "truncate" (this can
                      ;; either cut off bits, or pad the result with nulls)
                      (truncate bmp-size)

                      ;; finally convert back to something sensible
                      bmp->png

                      ;; and write it out
                      out)))))))
