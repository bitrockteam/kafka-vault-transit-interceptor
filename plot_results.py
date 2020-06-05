import numpy as np
import matplotlib.pyplot as plt
import os
import glob

def plot_kafka_output(directory, kind):
    numMsg = ""
    for TYPE in ["baseline", "interceptor"]:
        X = np.empty(0, dtype=float)
        Y = np.empty(0, dtype=float)
        print(TYPE)
        for filename in sorted(glob.iglob(f"{directory}/{kind}-{TYPE}*.txt")):
            size = filename.split('-')[-1].split('.')[0]
            numMsg =  filename.split('-')[-2]
            plt.title(f"{kind} perf {numMsg} msgs")
            plt.xlabel("Message Size [byte]")
            X = np.append(X, size)
            print(filename)
            with open(filename, 'r') as f:
                lines = f.read().splitlines()
                last_line = lines[-1]
                throughput = "0"
                if kind == "producer":
                    throughput = last_line.split(',')[1].split(' ')[1]
                    plt.ylabel("records/sec")
                else:
                    throughput = last_line.split(',')[3]
                    plt.ylabel("MB/sec")
                Y = np.append(Y, round(float(throughput), 2))
        print(X)
        print(Y)
        plt.scatter(X, Y, label=f"{TYPE}")

    plt.legend()
    plt.savefig(f"{directory}/{kind}-{numMsg}.png")
    plt.clf()

def main():
    plot_kafka_output("results", "producer")
    plot_kafka_output("results", "consumer")

if __name__ == "__main__":
    main()