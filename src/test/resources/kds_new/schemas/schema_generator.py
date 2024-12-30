import os
import yaml
import json
from genson import SchemaBuilder

# Specify the folder containing YAML files
folder_path = '../'
output_schema_file = os.path.join('./', 'fhirConnect_model_extension_schema.json')

# Initialize SchemaBuilder
builder = SchemaBuilder()

# Walk through the folder and all subfolders
for root, dirs, files in os.walk(folder_path):
    for filename in files:
        # Check if the file does not contain '.context' and is a YAML file
        if '.context' not in filename and (filename.endswith('.yaml') or filename.endswith('.yml')):
            file_path = os.path.join(root, filename)

            # Load the content of the YAML file
            with open(file_path, 'r') as f:
                datastore = yaml.safe_load(f)
                # Add the YAML content to the schema builder
                builder.add_object(datastore)

# Generate the schema
schema = builder.to_schema()

# Write the schema to a JSON file
with open(output_schema_file, 'w') as schema_file:
    json.dump(schema, schema_file, indent=4)

print(f"Schema written to: {output_schema_file}")