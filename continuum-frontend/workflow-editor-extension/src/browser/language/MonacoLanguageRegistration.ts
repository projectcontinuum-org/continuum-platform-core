import { injectable } from '@theia/core/shared/inversify';
import * as monaco from '@theia/monaco-editor-core';

// Static imports for all language modules - webpack needs these to be explicit
// @ts-expect-error - No type declarations for monaco language modules
import * as abap from 'monaco-editor/esm/vs/basic-languages/abap/abap';
// @ts-expect-error
import * as apex from 'monaco-editor/esm/vs/basic-languages/apex/apex';
// @ts-expect-error
import * as azcli from 'monaco-editor/esm/vs/basic-languages/azcli/azcli';
// @ts-expect-error
import * as bat from 'monaco-editor/esm/vs/basic-languages/bat/bat';
// @ts-expect-error
import * as bicep from 'monaco-editor/esm/vs/basic-languages/bicep/bicep';
// @ts-expect-error
import * as cameligo from 'monaco-editor/esm/vs/basic-languages/cameligo/cameligo';
// @ts-expect-error
import * as clojure from 'monaco-editor/esm/vs/basic-languages/clojure/clojure';
// @ts-expect-error
import * as coffee from 'monaco-editor/esm/vs/basic-languages/coffee/coffee';
// @ts-expect-error
import * as cpp from 'monaco-editor/esm/vs/basic-languages/cpp/cpp';
// @ts-expect-error
import * as csharp from 'monaco-editor/esm/vs/basic-languages/csharp/csharp';
// @ts-expect-error
import * as csp from 'monaco-editor/esm/vs/basic-languages/csp/csp';
// @ts-expect-error
import * as css from 'monaco-editor/esm/vs/basic-languages/css/css';
// @ts-expect-error
import * as cypher from 'monaco-editor/esm/vs/basic-languages/cypher/cypher';
// @ts-expect-error
import * as dart from 'monaco-editor/esm/vs/basic-languages/dart/dart';
// @ts-expect-error
import * as dockerfile from 'monaco-editor/esm/vs/basic-languages/dockerfile/dockerfile';
// @ts-expect-error
import * as ecl from 'monaco-editor/esm/vs/basic-languages/ecl/ecl';
// @ts-expect-error
import * as elixir from 'monaco-editor/esm/vs/basic-languages/elixir/elixir';
// @ts-expect-error
import * as flow9 from 'monaco-editor/esm/vs/basic-languages/flow9/flow9';
// @ts-expect-error
import * as freemarker2 from 'monaco-editor/esm/vs/basic-languages/freemarker2/freemarker2';
// @ts-expect-error
import * as fsharp from 'monaco-editor/esm/vs/basic-languages/fsharp/fsharp';
// @ts-expect-error
import * as go from 'monaco-editor/esm/vs/basic-languages/go/go';
// @ts-expect-error
import * as graphql from 'monaco-editor/esm/vs/basic-languages/graphql/graphql';
// @ts-expect-error
import * as handlebars from 'monaco-editor/esm/vs/basic-languages/handlebars/handlebars';
// @ts-expect-error
import * as hcl from 'monaco-editor/esm/vs/basic-languages/hcl/hcl';
// @ts-expect-error
import * as html from 'monaco-editor/esm/vs/basic-languages/html/html';
// @ts-expect-error
import * as ini from 'monaco-editor/esm/vs/basic-languages/ini/ini';
// @ts-expect-error
import * as java from 'monaco-editor/esm/vs/basic-languages/java/java';
// @ts-expect-error
import * as javascript from 'monaco-editor/esm/vs/basic-languages/javascript/javascript';
// @ts-expect-error
import * as julia from 'monaco-editor/esm/vs/basic-languages/julia/julia';
// @ts-expect-error
import * as kotlin from 'monaco-editor/esm/vs/basic-languages/kotlin/kotlin';
// @ts-expect-error
import * as less from 'monaco-editor/esm/vs/basic-languages/less/less';
// @ts-expect-error
import * as lexon from 'monaco-editor/esm/vs/basic-languages/lexon/lexon';
// @ts-expect-error
import * as lua from 'monaco-editor/esm/vs/basic-languages/lua/lua';
// @ts-expect-error
import * as liquid from 'monaco-editor/esm/vs/basic-languages/liquid/liquid';
// @ts-expect-error
import * as m3 from 'monaco-editor/esm/vs/basic-languages/m3/m3';
// @ts-expect-error
import * as markdown from 'monaco-editor/esm/vs/basic-languages/markdown/markdown';
// @ts-expect-error
import * as mdx from 'monaco-editor/esm/vs/basic-languages/mdx/mdx';
// @ts-expect-error
import * as mips from 'monaco-editor/esm/vs/basic-languages/mips/mips';
// @ts-expect-error
import * as msdax from 'monaco-editor/esm/vs/basic-languages/msdax/msdax';
// @ts-expect-error
import * as mysql from 'monaco-editor/esm/vs/basic-languages/mysql/mysql';
// @ts-expect-error
import * as objectivec from 'monaco-editor/esm/vs/basic-languages/objective-c/objective-c';
// @ts-expect-error
import * as pascal from 'monaco-editor/esm/vs/basic-languages/pascal/pascal';
// @ts-expect-error
import * as pascaligo from 'monaco-editor/esm/vs/basic-languages/pascaligo/pascaligo';
// @ts-expect-error
import * as perl from 'monaco-editor/esm/vs/basic-languages/perl/perl';
// @ts-expect-error
import * as pgsql from 'monaco-editor/esm/vs/basic-languages/pgsql/pgsql';
// @ts-expect-error
import * as php from 'monaco-editor/esm/vs/basic-languages/php/php';
// @ts-expect-error
import * as pla from 'monaco-editor/esm/vs/basic-languages/pla/pla';
// @ts-expect-error
import * as postiats from 'monaco-editor/esm/vs/basic-languages/postiats/postiats';
// @ts-expect-error
import * as powerquery from 'monaco-editor/esm/vs/basic-languages/powerquery/powerquery';
// @ts-expect-error
import * as powershell from 'monaco-editor/esm/vs/basic-languages/powershell/powershell';
// @ts-expect-error
import * as protobuf from 'monaco-editor/esm/vs/basic-languages/protobuf/protobuf';
// @ts-expect-error
import * as pug from 'monaco-editor/esm/vs/basic-languages/pug/pug';
// @ts-expect-error
import * as python from 'monaco-editor/esm/vs/basic-languages/python/python';
// @ts-expect-error
import * as qsharp from 'monaco-editor/esm/vs/basic-languages/qsharp/qsharp';
// @ts-expect-error
import * as r from 'monaco-editor/esm/vs/basic-languages/r/r';
// @ts-expect-error
import * as razor from 'monaco-editor/esm/vs/basic-languages/razor/razor';
// @ts-expect-error
import * as redis from 'monaco-editor/esm/vs/basic-languages/redis/redis';
// @ts-expect-error
import * as redshift from 'monaco-editor/esm/vs/basic-languages/redshift/redshift';
// @ts-expect-error
import * as restructuredtext from 'monaco-editor/esm/vs/basic-languages/restructuredtext/restructuredtext';
// @ts-expect-error
import * as ruby from 'monaco-editor/esm/vs/basic-languages/ruby/ruby';
// @ts-expect-error
import * as rust from 'monaco-editor/esm/vs/basic-languages/rust/rust';
// @ts-expect-error
import * as sb from 'monaco-editor/esm/vs/basic-languages/sb/sb';
// @ts-expect-error
import * as scala from 'monaco-editor/esm/vs/basic-languages/scala/scala';
// @ts-expect-error
import * as scheme from 'monaco-editor/esm/vs/basic-languages/scheme/scheme';
// @ts-expect-error
import * as scss from 'monaco-editor/esm/vs/basic-languages/scss/scss';
// @ts-expect-error
import * as shell from 'monaco-editor/esm/vs/basic-languages/shell/shell';
// @ts-expect-error
import * as solidity from 'monaco-editor/esm/vs/basic-languages/solidity/solidity';
// @ts-expect-error
import * as sophia from 'monaco-editor/esm/vs/basic-languages/sophia/sophia';
// @ts-expect-error
import * as sparql from 'monaco-editor/esm/vs/basic-languages/sparql/sparql';
// @ts-expect-error
import * as sql from 'monaco-editor/esm/vs/basic-languages/sql/sql';
// @ts-expect-error
import * as st from 'monaco-editor/esm/vs/basic-languages/st/st';
// @ts-expect-error
import * as swift from 'monaco-editor/esm/vs/basic-languages/swift/swift';
// @ts-expect-error
import * as systemverilog from 'monaco-editor/esm/vs/basic-languages/systemverilog/systemverilog';
// @ts-expect-error
import * as tcl from 'monaco-editor/esm/vs/basic-languages/tcl/tcl';
// @ts-expect-error
import * as twig from 'monaco-editor/esm/vs/basic-languages/twig/twig';
// @ts-expect-error
import * as typescript from 'monaco-editor/esm/vs/basic-languages/typescript/typescript';
// @ts-expect-error
import * as typespec from 'monaco-editor/esm/vs/basic-languages/typespec/typespec';
// @ts-expect-error
import * as vb from 'monaco-editor/esm/vs/basic-languages/vb/vb';
// @ts-expect-error
import * as wgsl from 'monaco-editor/esm/vs/basic-languages/wgsl/wgsl';
// @ts-expect-error
import * as xml from 'monaco-editor/esm/vs/basic-languages/xml/xml';
// @ts-expect-error
import * as yaml from 'monaco-editor/esm/vs/basic-languages/yaml/yaml';

/**
 * Registers Monaco language support with full syntax highlighting.
 * Imports language definitions from monaco-editor and registers them with Theia's Monaco instance.
 * Provides syntax highlighting for 80+ languages including Kotlin, Python, Java, SQL, FreeMarker, and more.
 */
@injectable()
export class MonacoLanguageRegistration {

    // Map of language IDs to their module exports
    private languageModules: Record<string, any> = {
        'abap': abap,
        'apex': apex,
        'azcli': azcli,
        'bat': bat,
        'bicep': bicep,
        'cameligo': cameligo,
        'clojure': clojure,
        'coffee': coffee,
        'cpp': cpp,
        'csharp': csharp,
        'csp': csp,
        'css': css,
        'cypher': cypher,
        'dart': dart,
        'dockerfile': dockerfile,
        'ecl': ecl,
        'elixir': elixir,
        'flow9': flow9,
        'freemarker2': freemarker2,
        'fsharp': fsharp,
        'go': go,
        'graphql': graphql,
        'handlebars': handlebars,
        'hcl': hcl,
        'html': html,
        'ini': ini,
        'java': java,
        'javascript': javascript,
        'julia': julia,
        'kotlin': kotlin,
        'less': less,
        'lexon': lexon,
        'lua': lua,
        'liquid': liquid,
        'm3': m3,
        'markdown': markdown,
        'mdx': mdx,
        'mips': mips,
        'msdax': msdax,
        'mysql': mysql,
        'objective-c': objectivec,
        'pascal': pascal,
        'pascaligo': pascaligo,
        'perl': perl,
        'pgsql': pgsql,
        'php': php,
        'pla': pla,
        'postiats': postiats,
        'powerquery': powerquery,
        'powershell': powershell,
        'protobuf': protobuf,
        'pug': pug,
        'python': python,
        'qsharp': qsharp,
        'r': r,
        'razor': razor,
        'redis': redis,
        'redshift': redshift,
        'restructuredtext': restructuredtext,
        'ruby': ruby,
        'rust': rust,
        'sb': sb,
        'scala': scala,
        'scheme': scheme,
        'scss': scss,
        'shell': shell,
        'solidity': solidity,
        'sophia': sophia,
        'sparql': sparql,
        'sql': sql,
        'st': st,
        'swift': swift,
        'systemverilog': systemverilog,
        'tcl': tcl,
        'twig': twig,
        'typescript': typescript,
        'typespec': typespec,
        'vb': vb,
        'wgsl': wgsl,
        'xml': xml,
        'yaml': yaml
    };

    private languageDefinitions = [
        { id: 'abap', extensions: ['.abap'], aliases: ['ABAP', 'abap'] },
        { id: 'apex', extensions: ['.apex'], aliases: ['Apex', 'apex'] },
        { id: 'azcli', extensions: ['.azcli'], aliases: ['Azure CLI', 'azcli'] },
        { id: 'bat', extensions: ['.bat', '.cmd'], aliases: ['Batch', 'bat'] },
        { id: 'bicep', extensions: ['.bicep'], aliases: ['Bicep', 'bicep'] },
        { id: 'cameligo', extensions: ['.mligo'], aliases: ['Cameligo', 'cameligo'] },
        { id: 'clojure', extensions: ['.clj', '.cljs', '.cljc', '.edn'], aliases: ['Clojure', 'clojure'] },
        { id: 'coffee', extensions: ['.coffee'], aliases: ['CoffeeScript', 'coffee'] },
        { id: 'cpp', extensions: ['.cpp', '.cc', '.cxx', '.hpp', '.hh', '.hxx', '.h'], aliases: ['C++', 'cpp'] },
        { id: 'csharp', extensions: ['.cs', '.csx', '.cake'], aliases: ['C#', 'csharp'] },
        { id: 'csp', extensions: [], aliases: ['CSP', 'csp'] },
        { id: 'css', extensions: ['.css'], aliases: ['CSS', 'css'] },
        { id: 'cypher', extensions: ['.cypher', '.cyp'], aliases: ['Cypher', 'cypher'] },
        { id: 'dart', extensions: ['.dart'], aliases: ['Dart', 'dart'] },
        { id: 'dockerfile', extensions: ['.dockerfile'], aliases: ['Dockerfile', 'dockerfile'] },
        { id: 'ecl', extensions: ['.ecl'], aliases: ['ECL', 'ecl'] },
        { id: 'elixir', extensions: ['.ex', '.exs'], aliases: ['Elixir', 'elixir'] },
        { id: 'flow9', extensions: ['.flow'], aliases: ['Flow9', 'flow9'] },
        { id: 'freemarker2', extensions: ['.ftl'], aliases: ['FreeMarker', 'freemarker2'] },
        { id: 'fsharp', extensions: ['.fs', '.fsi', '.ml', '.mli', '.fsx', '.fsscript'], aliases: ['F#', 'fsharp'] },
        { id: 'go', extensions: ['.go'], aliases: ['Go', 'go'] },
        { id: 'graphql', extensions: ['.graphql', '.gql'], aliases: ['GraphQL', 'graphql'] },
        { id: 'handlebars', extensions: ['.handlebars', '.hbs'], aliases: ['Handlebars', 'handlebars'] },
        { id: 'hcl', extensions: ['.tf', '.tfvars', '.hcl'], aliases: ['HCL', 'hcl'] },
        { id: 'html', extensions: ['.html', '.htm', '.shtml', '.xhtml', '.mdoc', '.jsp', '.asp', '.aspx', '.jshtm'], aliases: ['HTML', 'html'] },
        { id: 'ini', extensions: ['.ini', '.properties', '.gitconfig'], aliases: ['Ini', 'ini'] },
        { id: 'java', extensions: ['.java', '.jav'], aliases: ['Java', 'java'] },
        { id: 'javascript', extensions: ['.js', '.es6', '.jsx', '.mjs', '.cjs'], aliases: ['JavaScript', 'javascript'] },
        { id: 'julia', extensions: ['.jl'], aliases: ['Julia', 'julia'] },
        { id: 'kotlin', extensions: ['.kt', '.kts'], aliases: ['Kotlin', 'kotlin'] },
        { id: 'less', extensions: ['.less'], aliases: ['Less', 'less'] },
        { id: 'lexon', extensions: ['.lex'], aliases: ['Lexon', 'lexon'] },
        { id: 'lua', extensions: ['.lua'], aliases: ['Lua', 'lua'] },
        { id: 'liquid', extensions: ['.liquid'], aliases: ['Liquid', 'liquid'] },
        { id: 'm3', extensions: ['.m3', '.i3', '.mg', '.ig'], aliases: ['Modula-3', 'm3'] },
        { id: 'markdown', extensions: ['.md', '.markdown', '.mdown', '.mkdn', '.mkd', '.mdwn', '.mdtxt', '.mdtext'], aliases: ['Markdown', 'markdown'] },
        { id: 'mdx', extensions: ['.mdx'], aliases: ['MDX', 'mdx'] },
        { id: 'mips', extensions: ['.s'], aliases: ['MIPS', 'mips'] },
        { id: 'msdax', extensions: ['.dax', '.msdax'], aliases: ['DAX', 'msdax'] },
        { id: 'mysql', extensions: [], aliases: ['MySQL', 'mysql'] },
        { id: 'objective-c', extensions: ['.m'], aliases: ['Objective-C', 'objective-c'] },
        { id: 'pascal', extensions: ['.pas', '.p', '.pp'], aliases: ['Pascal', 'pascal'] },
        { id: 'pascaligo', extensions: ['.ligo'], aliases: ['Pascaligo', 'pascaligo'] },
        { id: 'perl', extensions: ['.pl', '.pm'], aliases: ['Perl', 'perl'] },
        { id: 'pgsql', extensions: [], aliases: ['PostgreSQL', 'pgsql'] },
        { id: 'php', extensions: ['.php', '.php4', '.php5', '.phtml', '.ctp'], aliases: ['PHP', 'php'] },
        { id: 'pla', extensions: ['.pla'], aliases: ['PLA', 'pla'] },
        { id: 'postiats', extensions: ['.dats', '.sats', '.hats'], aliases: ['ATS', 'postiats'] },
        { id: 'powerquery', extensions: ['.pq', '.pqm'], aliases: ['Power Query', 'powerquery'] },
        { id: 'powershell', extensions: ['.ps1', '.psm1', '.psd1'], aliases: ['PowerShell', 'powershell'] },
        { id: 'protobuf', extensions: ['.proto'], aliases: ['Protobuf', 'protobuf'] },
        { id: 'pug', extensions: ['.pug', '.jade'], aliases: ['Pug', 'pug'] },
        { id: 'python', extensions: ['.py', '.rpy', '.pyw', '.cpy', '.gyp', '.gypi'], aliases: ['Python', 'python'] },
        { id: 'qsharp', extensions: ['.qs'], aliases: ['Q#', 'qsharp'] },
        { id: 'r', extensions: ['.r', '.rhistory', '.rmd', '.rprofile', '.rt'], aliases: ['R', 'r'] },
        { id: 'razor', extensions: ['.cshtml'], aliases: ['Razor', 'razor'] },
        { id: 'redis', extensions: ['.redis'], aliases: ['Redis', 'redis'] },
        { id: 'redshift', extensions: [], aliases: ['Redshift', 'redshift'] },
        { id: 'restructuredtext', extensions: ['.rst'], aliases: ['reStructuredText', 'restructuredtext'] },
        { id: 'ruby', extensions: ['.rb', '.rbx', '.rjs', '.gemspec', '.pp'], aliases: ['Ruby', 'ruby'] },
        { id: 'rust', extensions: ['.rs', '.rlib'], aliases: ['Rust', 'rust'] },
        { id: 'sb', extensions: ['.sb'], aliases: ['Small Basic', 'sb'] },
        { id: 'scala', extensions: ['.scala', '.sc', '.sbt'], aliases: ['Scala', 'scala'] },
        { id: 'scheme', extensions: ['.scm', '.ss', '.sch', '.rkt'], aliases: ['Scheme', 'scheme'] },
        { id: 'scss', extensions: ['.scss'], aliases: ['SCSS', 'scss'] },
        { id: 'shell', extensions: ['.sh', '.bash'], aliases: ['Shell', 'shell'] },
        { id: 'solidity', extensions: ['.sol'], aliases: ['Solidity', 'solidity'] },
        { id: 'sophia', extensions: ['.aes'], aliases: ['Sophia', 'sophia'] },
        { id: 'sparql', extensions: ['.rq'], aliases: ['SPARQL', 'sparql'] },
        { id: 'sql', extensions: ['.sql'], aliases: ['SQL', 'sql'] },
        { id: 'st', extensions: ['.st', '.iecst', '.iecplc', '.lc3lib'], aliases: ['Structured Text', 'st'] },
        { id: 'swift', extensions: ['.swift'], aliases: ['Swift', 'swift'] },
        { id: 'systemverilog', extensions: ['.sv', '.svh'], aliases: ['SystemVerilog', 'systemverilog'] },
        { id: 'tcl', extensions: ['.tcl'], aliases: ['Tcl', 'tcl'] },
        { id: 'twig', extensions: ['.twig'], aliases: ['Twig', 'twig'] },
        { id: 'typescript', extensions: ['.ts', '.tsx', '.cts', '.mts'], aliases: ['TypeScript', 'typescript'] },
        { id: 'typespec', extensions: ['.tsp'], aliases: ['TypeSpec', 'typespec'] },
        { id: 'vb', extensions: ['.vb'], aliases: ['Visual Basic', 'vb'] },
        { id: 'wgsl', extensions: ['.wgsl'], aliases: ['WGSL', 'wgsl'] },
        { id: 'xml', extensions: ['.xml', '.xsd', '.dtd', '.ascx', '.csproj', '.config', '.wxi', '.wxl', '.wxs', '.xaml', '.svg', '.svgz'], aliases: ['XML', 'xml'] },
        { id: 'yaml', extensions: ['.yaml', '.yml'], aliases: ['YAML', 'yaml'] }
    ];

    initialize(): void {
        // Register all language definitions upfront
        for (const langDef of this.languageDefinitions) {
            this.registerLanguage(langDef.id, langDef.extensions, langDef.aliases, []);
        }

        // Set up language configuration - apply immediately since modules are already loaded
        for (const langDef of this.languageDefinitions) {
            monaco.languages.onLanguageEncountered(langDef.id, () => {
                try {
                    const mod = this.languageModules[langDef.id];
                    if (mod.language) {
                        monaco.languages.setMonarchTokensProvider(langDef.id, mod.language);
                    }
                    if (mod.conf) {
                        monaco.languages.setLanguageConfiguration(langDef.id, mod.conf);
                    }
                    console.log(`Loaded syntax highlighting for ${langDef.id}`);
                } catch (error) {
                    console.error(`Failed to load language ${langDef.id}:`, error);
                }
            });
        }

        console.log(`Monaco languages registered: ${this.languageDefinitions.length} languages available with syntax highlighting support`);
    }

    private registerLanguage(
        id: string,
        extensions: string[],
        aliases: string[],
        mimetypes: string[]
    ): void {
        try {
            monaco.languages.register({
                id,
                extensions,
                aliases,
                mimetypes
            });
        } catch (error) {
            console.debug(`Language ${id} already registered or failed to register:`, error);
        }
    }
}
