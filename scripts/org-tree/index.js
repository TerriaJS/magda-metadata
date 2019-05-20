#!/usr/bin/env node
const pkg = require("../package.json");
const program = require("commander");

program
    .version(pkg.version)
    .description(
        `A tool for managing magda authentication & access control data. Version: ${
            pkg.version
        }\n\n` +
            `If a database connection is required, the following environment variables will be used to create a connection:\n` +
            `  POSTGRES_HOST: database host; If not available in env var, 'localhost' will be used.\n` +
            `  POSTGRES_DB: database name; If not available in env var, 'auth' will be used.\n` +
            `  POSTGRES_USER: database username; If not available in env var, 'postgres' will be used.\n` +
            `  POSTGRES_PASSWORD: database password; If not available in env var, '' will be used.`
    )
    .command("view", "\n\tDisplay the entire tree in text .\n")
    .command(
        "create <rootNodeName>",
        "\n\tCreate a root tree node with specified name.\n"
    )
    .command(
        "insert <nodeName> <parentNodeNameOrId>",
        "\n\tInsert a node as a child node of the specified the parent node with specified name. \n" +
            "\tIf the parent node name is given instead of the parent node Id, the newly created child node will be inserted to the first located parent node.\n"
    )
    .command(
        "delete <nodeNameOrId> -o, --only",
        "\n\tDelete the node specified and all its dependents from the tree. \n" +
            "\tIf the node name is given instead of the node Id, the first located node (and its dependents) will be removed.\n"
    )
    .command(
        "delete-one <nodeNameOrId> ",
        "\n\tDelete the node specified only from the tree. \n" +
            "\tthe deleted node's dependents (if any) will become the specifed node's parent's children. \n" +
            "\tIf the node name is given instead of the node Id, the first located node will be removed.\n"
    )
    .command(
        "move <nodeNameOrId> <parentNodeNameOrId>",
        "\n\tMove the node specified and all its dependents to the specified parent node. \n" +
            "\tIf the node name is given instead of the node Id, the first located node (and its dependents) will be moved.\n" +
            "\tIf the parent node name is given instead of the parent node Id, the specifed node will be moved to the first located parent node.\n"
    )
    .parse(process.argv);
