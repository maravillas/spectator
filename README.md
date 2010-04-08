# Spectator

Spectator is a Clojure library that provides a system for observing changes to maps. It's similar in spirit to Clojure's [add-watch](http://richhickey.github.com/clojure/clojure.core-api.html#clojure.core/add-watch) mechanism for vars, refs, agents, and atoms, but differs in certain important regards.

## Usage

### The Basics

Watchers are functions that take two arguments, the previous map and the new map. They are expected to return a map of any further changes to apply to the map.

    (defn celsius-to-fahrenheit [c]
      (Math/round (+ (* c (/ 9.0 5)) 32)))

    (defn update-fahrenheit [old new]
      {:fahrenheit (celsius-to-fahrenheit (:celsius new))})

    (defn fahrenheit-to-celsius [f]
      (Math/round (* (- f 32) (/ 5.0 9))))

    (defn update-celsius [old new]
      {:celsius (fahrenheit-to-celsius (:fahrenheit new))})

Add watchers to maps using <tt>watch-keys</tt>, specifying the key(s) they should observe. Modify the map using <tt>update</tt>.
    
    (let [map {}
          map (watch-keys map update-fahrenheit :celsius)
	  map (update map {:celsius 15})]
      map)

    => {:celsius 15, :fahrenheit 59}

Correspondingly, watchers can be removed using <tt>unwatch-keys</tt>.

### Watcher Details

Applicable watchers are run in the order they were added with each change to the map, whether the change originated through a call to <tt>update</tt> or as a result of another watcher.

    (defn celsius-to-kelvin [c]
      (+ c 273.15))

    (defn update-kelvin [old new]
      {:kelvin (celsius-to-kelvin (:celsius new))})

    (let [map (-> {}
     	          (watch-keys update-celsius :fahrenheit)
		  (watch-keys update-kelvin :celsius)
		  (update {:fahrenheit 212}))]
      map)		 		

    => {:fahrenheit 212, :celsius 100, :kelvin 373.15}

Cycles of watchers are fine, but you need to ensure that the values will eventually converge. Expanding on the celsius/fahrenheit example above:

    (def m (-> {}
               (watch-keys update-fahrenheit :celsius)
	       (watch-keys update-celsius :fahrenheit)))

    (update m {:celsius 17})

    => {:celsius 17, :fahrenheit 63}

    (update m {:fahrenheit 19})

    => {:fahrenheit 19, :celsius -7}

Updates that don't change the map won't trigger the watchers. 

    (let [map (-> {:fahrenheit 0 :celsius 0}
                  (watch-keys update-fahrenheit :celsius)
		  (update {:celsius 0}))]
      map)

    => {:fahrenheit 0, :celsius 0}

The <tt>touch</tt> function will run the watchers without making a change to the map.
  
    (let [map (-> {:fahrenheit 0 :celsius 0}
                  (watch-keys update-fahrenheit :celsius)
		  (touch :celsius))]
      map)

    => {:fahrenheit 32, :celsius 0}

### Silent Updates

The map can be updated without executing the relevant watchers by specifying <tt>true</tt> for the silent parameter. The default value is <tt>false</tt>.

    (let [map (-> {:fahrenheit 0 :celsius 0}
                  (watch-keys update-fahrenheit :celsius)
		  (update {:celsius 17} true))]
      map)  

    => {:fahrenheit 0, :celsius 17}

### Memos

Extra information can be sent to the watchers through <tt>update</tt> by specifying a map for the memo parameter. The default value is an empty map.

This map, along with an extra entry <tt>:initial-changes</tt>, is available to watchers by accessing the <tt>new</tt> parameter's metadata. Alternately, it can be read using the <tt>memo</tt> convenience function.

    (let [map (-> {}
    	          (watch-keys (fn [old new] {:info (memo new)}) :celsius)
		  (update {:celsius 7} false {:source "sensor 1"}))]
      map)

    => {:celsius 7, :info {:source "sensor 1", :initial-changes {:celsius 7}}}

Watchers can modify the memo for subsequent watchers using <tt>with-memo</tt>. Changes to the memo will not result in further cycles of watchers.

    (let [map (-> {}
    	          (watch-keys (fn [old new]
		  	        (when (not (:enabled new))
				  (with-memo {:enabled true} {:handled true})))
		              :clicked)
		  (watch-keys (fn [old new]
		  	        (when (:handled (memo new))
				  {:complete true}))
			      :clicked))]
      (update map {:clicked true}))

    => {:clicked true, :complete true, :enabled true}

    (let [map (-> {}
                  ;; As above
      (update map {:clicked true :enabled true}))

    => {:enabled true, :clicked true}

The memo is not available outside of the watchers.

    (let [map (-> {}
    	          (watch-keys (fn [old new] {:info (memo new)}) :foo)
		  (update {:foo true} false {:extra-info 42}))]
      (memo map))

    => nil

### Vetoing

Watchers can opt to veto all updates stemming from the initial update, including that initial update. Once a watcher vetoes an update, further watchers are not executed.

Veto updates by setting the <tt>:veto</tt> key in the memo to <tt>true</tt>, or by returning the result of the convenience function <tt>veto</tt>.

    (let [map (-> {}
    	       	  (watch-keys (fn [old new] (veto)) :celsius)
		  (watch-keys (fn [old new] update-fahrenheit) :celsius)
		  (update {:celsius 100}))]
      map)

    => {}

## Thanks

Thanks to [Patrick Quinn](http://github.com/bilts) for many ideas and much feedback.

## License

Copyright (c) 2010 Matthew Maravillas

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in
all copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
THE SOFTWARE.