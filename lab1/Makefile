.PHONY: build run run-all

build:
	javac -d out/classes $$(find src -type f -name '*.java')
	jar --create --file out/lab1.jar \
		--manifest MANIFEST.MF \
		-C out/classes \
		.

run: build
	java -jar out/lab1.jar $(ARGS)

run-all: build
	java -jar out/lab1.jar --collector single
	@for collector in mutex empty-mutex sharded-mutex; do \
		i=1; \
		while [ $$i -le $$(nproc) ]; do \
			java -jar out/lab1.jar --collector $$collector --threads $$i; \
			i=$$((i * 2)); \
		done; \
	done
