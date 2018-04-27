import argparse
import json

def load_annotations(path):
    print("Loading annotations from {}".format(path))

    with open(path) as f:
        return json.load(f)

def save_annotations(annotations, path):
    print("Saving annotations to {}".format(path))

    with open(path, "w") as f:
        json.dump(annotations, f, sort_keys=True, indent=4)

def join_annotations(annotations1, annotations2):
    print("Joining annotations")

    annotations1_only = 0
    annotations2_only = 0
    annotations1_better = 0
    annotations2_better = 0
    annotations_mismatch = 0
    annotations_same = 0
    annotations_cross_improve = 0

    joined_annotations = {}

    for name, source_files in annotations1.items():
        for source_file, annotation1 in source_files.items():
            if name not in annotations2 or source_file not in annotations2[name]:
                joined_annotations.setdefault(name, {})[source_file] = annotation1
                annotations1_only += 1
                continue

            annotation2 = annotations2[name][source_file]

            joined_annotation = {
                "object file": annotation1["object file"],
                "signature": annotation1["signature"],
                "params": []
            }

            annotation1_has_better_param = False
            annotation2_has_better_param = False
            has_mismatch = False

            for param1, param2 in zip(annotation1["params"], annotation2["params"]):
                if param1["is pointer"] != param2["is pointer"] or param1["name"] != param2["name"]:
                    has_mismatch = True
                    break

                joined_param = {
                    "name": param1["name"],
                    "is pointer": param1["is pointer"]
                }

                if joined_param["is pointer"]:
                    joined_param["must deref"] = param1["must deref"] or param2["must deref"]

                    if param1["must deref"] != param2["must deref"]:
                        if param1["must deref"]:
                            annotation1_has_better_param = True
                        else:
                            annotation2_has_better_param = True

                    joined_param["may deref"] = param1["may deref"] and param2["may deref"]

                    if param1["may deref"] != param2["may deref"]:
                        if not param1["may deref"]:
                            annotation1_has_better_param = True
                        else:
                            annotation2_has_better_param = True

                joined_annotation["params"].append(joined_param)

            if has_mismatch:
                annotations_mismatch += 1
                joined_annotation = annotation1

            joined_annotations.setdefault(name, {})[source_file] = joined_annotation

            if has_mismatch:
                continue

            if annotation1_has_better_param:
                if annotation2_has_better_param:
                    annotations_cross_improve += 1
                else:
                    annotations1_better += 1
            else:
                if annotation2_has_better_param:
                    annotations2_better += 1
                else:
                    annotations_same += 1

    for name, source_files in annotations2.items():
        for source_file, annotation2 in source_files.items():
            if name not in annotation1 or source_file not in annotations1[name]:
                joined_annotation.setdefault(name, {})[source_file] = annotation2
                annotations2_only += 1

    print("For {} functions only annotations1 has an annotation".format(annotations1_only))
    print("For {} functions only annotations2 has an annotation".format(annotations2_only))
    print("For {} functions annotations1 has better annotation".format(annotations1_better))
    print("For {} functions annotations2 has better annotation".format(annotations2_better))
    print("For {} functions annotations are same".format(annotations_same))
    print("For {} functions annotations complete each other".format(annotations_cross_improve))
    print("For {} functions param names or types mismatched".format(annotations_mismatch))

    return joined_annotation

def main():
    parser = argparse.ArgumentParser(
        description="Joins two annotation files.")

    parser.add_argument(
        "annotations1",
        help="Path to the first JSON file with annotations.")

    parser.add_argument(
        "annotations2",
        help="Path to the second JSON file with annotations.")

    parser.add_argument(
        "joined_annotations",
        help="Path to output joined annotations.")

    args = parser.parse_args()

    annotations1 = load_annotations(args.annotations1)
    annotations2 = load_annotations(args.annotations2)
    joined_annotations = join_annotations(annotations1, annotations2)
    save_annotations(joined_annotations, args.joined_annotations)

if __name__ == "__main__":
    main()