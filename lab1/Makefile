.PHONY: build run run-all

RUN_JAR=java -jar out/lab1.jar

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
	@for collector in mutex empty-mutex striped-mutex thread-local; do \
		i=1; \
		while [ $$i -le $$(nproc) ]; do \
			$(RUN_JAR) --benchmark --collector $$collector --threads $$i; \
			i=$$((i * 2)); \
		done; \
	done

stress: build
	@$(RUN_JAR) --stress --threads $$(nproc) --collector mutex
	@$(RUN_JAR) --stress --threads $$(nproc) --collector striped-mutex
	@$(RUN_JAR) --stress --threads $$(nproc) --collector thread-local
