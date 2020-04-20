package com.graphqljava.bookdetails.provider;

import com.graphqljava.bookdetails.datafetchers.GraphQLDataFetchers;
import com.graphqljava.bookdetails.directives.BookViewReasonDirective;
import graphql.GraphQL;
import graphql.analysis.MaxQueryDepthInstrumentation;
import graphql.schema.GraphQLSchema;
import graphql.schema.idl.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@Component
public class GraphQLProvider {

    private GraphQL graphQL;

    @Autowired
    private GraphQLDataFetchers graphQLDataFetchers;

    @Autowired
    private BookViewReasonDirective bookViewReasonDirective;

    @Value("classpath:schema/**/*.graphqls")
    private Resource[] schemaResources;

    @Bean
    public GraphQL graphQL() {
        return graphQL;
    }

    @PostConstruct
    public void init() {
        final List<File> schemas = Arrays.stream(schemaResources).filter(Resource::isFile).map(resource -> {
            try {
                return resource.getFile();
            } catch (IOException ex) {
                throw new RuntimeException("Unable to load schema files");
            }
        }).collect(Collectors.toList());

        final GraphQLSchema graphQLSchema = buildSchema(schemas);

        this.graphQL = GraphQL
                .newGraphQL(graphQLSchema)
                .build();
    }

    /**
     * Mearch các file SDL thành 1 schema
     *
     * @param schemas danh sách các file schemas
     * @return
     */
    private GraphQLSchema buildSchema(final List<File> schemas) {
        final SchemaParser schemaParser = new SchemaParser();
        final SchemaGenerator schemaGenerator = new SchemaGenerator();
        final TypeDefinitionRegistry typeDefinitionRegistry = new TypeDefinitionRegistry();

        for (final File schema : schemas) {
            typeDefinitionRegistry.merge(schemaParser.parse(schema));
        }

        final RuntimeWiring runtimeWiring = buildWiring();
        return schemaGenerator.makeExecutableSchema(typeDefinitionRegistry, runtimeWiring);
    }

    /**
     * 1. DataFetcher:
     * Ứng với mỗi type sẽ có các field
     * Ứng với mỗi field sẽ có từng DataFetcher
     * DataFetcher là nơi sẽ lấy các tham số từ query và truy vấn xuống CSDL để lấy dữ liệu trả về
     * 2. Directive
     * Ứng với 1 directive phải có 1 SchemaDirectiveWiring
     * SchemaDirectiveWiring: là nơi sẽ kiểm tra điều kiện field đó có được hiển thị hay không
     * 3. Luồng
     * DataFetcher --> SchemaDirectiveWiring (nếu trường đó có directive)
     *
     * @return
     */
    private RuntimeWiring buildWiring() {
        return RuntimeWiring.newRuntimeWiring()
                .type(TypeRuntimeWiring.newTypeWiring("Query").dataFetcher("bookById", graphQLDataFetchers.getBookByIdDataFetcher()))
                .type(TypeRuntimeWiring.newTypeWiring("Query").dataFetcher("listBooks", graphQLDataFetchers.listBooks()))
                .type(TypeRuntimeWiring.newTypeWiring("Book").dataFetcher("author", graphQLDataFetchers.getAuthorDataFetcher()))
                .type(TypeRuntimeWiring.newTypeWiring("Book").dataFetcher("bookStores", graphQLDataFetchers.getBookStores()))
                .type(TypeRuntimeWiring.newTypeWiring("Mutation").dataFetcher("editBookById",graphQLDataFetchers.saveBook()))
                .directive("bookViewReason", bookViewReasonDirective)
                .build();
    }
}
