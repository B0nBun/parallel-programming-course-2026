.PHONY: build run run-all

RUN_JAR=java -jar out/lab1.jar
# BENCHMARK_COLLECTORS=mutex empty-mutex striped-mutex thread-local double-buffer
BENCHMARK_COLLECTORS=striped-mutex
STRESS_COLLECTORS=mutex striped-mutex thread-local double-buffer

build:
	javac -d out/classes $$(find src -type f -name '*.java')
	jar --create --file out/lab1.jar \
		--manifest MANIFEST.MF \
		-C out/classes \
		.

run: build
	$(RUN_JAR) $(ARGS)

benchmark: build
	@$(RUN_JAR) --benchmark --collector single
	@for collector in $(BENCHMARK_COLLECTORS); do \
		i=1; \
		while [ $$i -le $$(nproc) ]; do \
			$(RUN_JAR) --benchmark --collector $$collector --threads $$i; \
			i=$$((i * 2)); \
		done; \
	done

stress: build
	@for collector in $(STRESS_COLLECTORS); do \
		$(RUN_JAR) --stress --threads 4 --collector $$collector; \
	done

property: build
	@for collector in $(STRESS_COLLECTORS); do \
		$(RUN_JAR) --property --collector $$collector; \
	done
