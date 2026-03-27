import os
import glob

template = """<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <parent>
        <groupId>com.ecommerce</groupId>
        <artifactId>ecommerce-platform</artifactId>
        <version>1.0.0-SNAPSHOT</version>
        <relativePath>../../pom.xml</relativePath>
    </parent>
    <artifactId>{name}</artifactId>
    <packaging>jar</packaging>
</project>"""

# Create missing user-service directory
os.makedirs("services/user-service", exist_ok=True)

dirs = glob.glob('services/*') + glob.glob('infrastructure/*')
for d in dirs:
    if os.path.isdir(d):
        name = os.path.basename(d)
        path = os.path.join(d, 'pom.xml')
        # Only overwrite if it is currently 0 bytes or essentially empty
        if not os.path.exists(path) or os.path.getsize(path) < 100:
            with open(path, 'w') as f:
                f.write(template.format(name=name))
