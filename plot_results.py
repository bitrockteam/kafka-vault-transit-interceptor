import glob

import matplotlib.pyplot as plt
import numpy as np


def plot_kafka_output(directory, kind):
  num_msg = ""
  for TYPE in ["baseline", "interceptor"]:
    array_x = np.empty(0, dtype=float)
    array_y = np.empty(0, dtype=float)
    print(TYPE)

    # grab last 4 characters of the file name:
    def message_size(x):
      print(x)
      print(x.split("-")[-1].rsplit(".", 1)[0])
      return int(x.split("-")[-1].rsplit(".", 1)[0])

    file_list = glob.iglob(f"{directory}/{kind}-{TYPE}*.txt")
    for filename in sorted(file_list, key=message_size):
      size = filename.split('-')[-1].split('.')[0]
      num_msg = filename.split('-')[-2]
      plt.title(f"{kind} perf {num_msg} msgs")
      plt.xlabel("Message Size [byte]")
      array_x = np.append(array_x, size)
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
        array_y = np.append(array_y, round(float(throughput), 2))
    print(array_x)
    print(array_y)
    plt.scatter(array_x, array_y, label=f"{TYPE}")

  plt.legend()
  plt.savefig(f"{directory}/{kind}-{num_msg}.png")
  plt.clf()


def main():
  plot_kafka_output("results", "producer")
  plot_kafka_output("results", "consumer")


if __name__ == "__main__":
  main()
