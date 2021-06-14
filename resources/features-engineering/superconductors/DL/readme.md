# Feature engineering experiments with DL

## Summary 

| Name | Changes | Precision | Recall  | F1 |  
|------|---------|-----------|---------|----|
| [baseline_no_features](baseline_no_features) | 172 papers, no features | 77.00  |  77.20 |   77.09 |
| [baseline_with_features](baseline_features) | 172 papers, features | 77.95  |  77.27  |  77.60  |
| oS | Oscar Small | - | -  | - |
| [fastText_oS_vec_skipgram_300d_no_features](fastText_oS_vec_skipgram_300d_no_features) | baseline_no_features + fastText + oscarS + skipgram + 300d |   76.26  |  74.64  |  75.4|
| [fastText_oS_bin_skipgram_300d_no_features](fastText_oS_bin_skipgram_300d_no_features) | baseline_no_features + fastText + oscarS + skipgram + 300d + bin |   75.23  |  74.88  |  75.04|
| [fastText_oS_vec_skipgram_300d_features](fastText_oS_vec_skipgram_300d_features) | baseline_features + fastText + oscarS + skipgram + 300d + vec |   76.32 |   75.76  |  76.01|
| [fastText_oS_bin_skipgram_300d_features](fastText_oS_bin_skipgram_300d_features) | baseline_features + fastText + oscarS + skipgram + 300d + bin |  75.95  |  75.97  |  75.94 |
| oS+Sm | Oscar Small + SuperMat | - | -  | - |
| [fastText_oS+Sm_vec_skipgram_300d_no_features](fastText_oS+Sm_vec_skipgram_300d_no_features) | baseline_no_features + fastText + oscarS_Supermat + skipgram + 300d + vec | 75.51  |  75.81 |   75.65  | 
| [fastText_oS+Sm_bin_skipgram_300d_no_features](fastText_oS+Sm_bin_skipgram_300d_no_features) | baseline_no_features + fastText + oscarS_Supermat + skipgram + 300d  + bin | 74.20  |  75.85  |  75.00  | 
| [fastText_oS+Sm_vec_skipgram_300d_features](fastText_oS+Sm_vec_skipgram_300d_features) | baseline_features + fastText + oscarS + Supermat + skipgram + 300d + vec|  76.19  |  76.76  |  76.46|
| [fastText_oS+Sm_bin_skipgram_300d_features](fastText_oS+Sm_bin_skipgram_300d_features) | baseline_features + fastText + oscarS + Supermat + skipgram + 300d + bin | 76.01  |  77.15  |  76.57 |
| oS+Sc | Oscar Small + SciCorpora | - | -  | - |
| [fastText_oS+Sc_vec_skipgram_300d_no_features](fastText_oS+Sc_vec_skipgram_300d_no_features) | baseline_no_features + fastText + oscarS + SciCorpora + skipgram + 300d + vec |  | 
| [fastText_oS+Sc_bin_skipgram_300d_no_features](fastText_oS+Sc_bin_skipgram_300d_no_features) | baseline_no_features + fastText + oscarS + SciCorpora + skipgram + 300d  + bin |75.43  |  76.56 |   75.99| 
| [fastText_oS+Sc_vec_skipgram_300d_features](fastText_oS+Sc_vec_skipgram_300d_features) | baseline_features + fastText + oscarS + SciCorpora + skipgram + 300d + vec | |
| [fastText_oS+Sc_bin_skipgram_300d_features](fastText_oS+Sc_bin_skipgram_300d_features) | baseline_features + fastText + oscarS + SciCorpora + skipgram + 300d + bin | 76.36   | 77.61  |  **76.97** |
| Sc | SciCorpora | - | -  | - |
| [fastText_Sc_vec_skipgram_300d_no_features](fastText_Sc_vec_skipgram_300d_no_features) | baseline_features + fastText + SciCorpora + skipgram + 300d + vec |   76.03  | 76.30  | 76.16 |
| [fastText_Sc_bin_skipgram_300d_no_features](fastText_Sc_bin_skipgram_300d_no_features) | baseline_features + fastText + SciCorpora + skipgram + 300d + bin |  75.78   | 76.57  |  76.16 |
| [fastText_Sc_vec_skipgram_300d_features](fastText_Sc_vec_skipgram_300d_features) | baseline_features + fastText + SciCorpora + skipgram + 300d + vec |   75.92  | 76.61  |  76.25 |
| [fastText_Sc_bin_skipgram_300d_features](fastText_Sc_bin_skipgram_300d_features) | baseline_features + fastText + SciCorpora + skipgram + 300d + bin |  76.14  |  77.46  |  76.79|
| Sc+Sm | SciCorpora + SuperMat | - | -  | - |
| [fastText_Sc+Sm_vec_skipgram_300d_no_features](fastText_Sc+Sm_vec_skipgram_300d_no_features) | baseline_features + fastText + SciCorpora + Supermat + skipgram + 300d + vec |   75.78  |  75.79  |  75.76|
| [fastText_Sc+Sm_bin_skipgram_300d_no_features](fastText_Sc+Sm_bin_skipgram_300d_no_features) | baseline_features + fastText + SciCorpora + Supermat + skipgram + 300d + bin |   75.93  |  76.71   | 76.31 |
| [fastText_Sc+Sm_vec_skipgram_300d_features](fastText_Sc+Sm_vec_skipgram_300d_features) | baseline_features + fastText + SciCorpora + Supermat + skipgram + 300d + vec |   76.42  |  76.34  |  76.37|
| [fastText_Sc+Sm_bin_skipgram_300d_features](fastText_Sc+Sm_bin_skipgram_300d_features) | baseline_features + fastText + SciCorpora + Supermat + skipgram + 300d + bin |   75.77   | 76.96  |  76.36|
| oL | Oscar Large | - | -  | - |
| oL+Sm| Oscar Large + SuperMat | - | -  | - |
| oL+Sc| Oscar Large + SciCorpora | - | -  | - |
| oL+Sc+Sm | Oscar Large + SciCorpora + SuperMat | - | -  | - |

# Embeddings 

| Name | Source corpus | Size | Vector dimension | 
|------|---------------|--------|------------------|
| oS | Oscar 1-12 | 4M vocab | 100, 300| | 
| oL | Oscar | 4M vocab | 300| [https://oscar-corpus.com/](Oscar), [https://github.com/lfoppiano/SuperMat](SuperMat) | 
| Sc | SciCorpora | - | 300| -  | - |  
| oS+Sm | Oscar 1-12 + Supermat | 4M vocab | 100, 300| [https://oscar-corpus.com/](Oscar), [https://github.com/lfoppiano/SuperMat](SuperMat) | 
| oS+Sc | Oscar 1-12 + SciCorpora | - | 300| - |
| oS+Sc+Sm | Oscar 1-12 + SciCorpora + SuperMat| - | 300| - |
| oL+Sm | Oscar + Supermat | 4M vocab | 300| [https://oscar-corpus.com/](Oscar), [https://github.com/lfoppiano/SuperMat](SuperMat) | 
| oL+Sc | Oscar + SciCorpora | - | 300| - |
| oL+Sc+Sm | Oscar + SciCorpora + SuperMat| - | 300| - |
| glove.840B.300d | Common Crawl | 840B tokens, 2.2M vocab, cased | 300 |https://nlp.stanford.edu/projects/glove/ | 
| crawl-300d-2M | Common Crawl | 600B [2M vocab? (trained with subword)] | 300 | https://fasttext.cc/docs/en/english-vectors.html | 

# Corpora

| Name | Description | Size | Url | 
|------|---------------|--------|------------------|
| oL | Oscar Large | - | [https://oscar-corpus.com/](Oscar) |
| oS | Oscar Small (1-12) | - |
| Sm | SuperMat | 172 articles on superconductors | [https://github.com/lfoppiano/SuperMat](SuperMat) |
| Sc | SciCorpora | - | N/A |


# Old results

Results in this tables are from settings that have been discarded for their poor performances:

| Name | Changes | Precision | Recall  | F1 |  
|------|---------|-----------|---------|----|
| [fastText_oS+Sm_vec_cbow_100d_no_features](old/fastText_oS+Sm_vec_cbow_100d_no_features) | baseline_no_features + fastText + oscarS_Supermat + cbow + 100d |   72.15 |   73.39 |   72.75 |
| [fastText_oS+Sm_vec_cbow_100d_features](old/fastText_oS+Sm_vec_cbow_100d_features) | baseline_features + fastText + oscarS_Supermat + cbow + 100d |74.06  |  73.92  |  73.98|
| [fastText_oS+Sm_vec_cbow_300d_no_features](old/fastText_oS+Sm_vec_cbow_300d_no_features) | baseline_no_features + fastText + oscarS_Supermat + cbow + 300d |   73.49  |  73.89  |  73.68  |
| [fastText_oS+Sm_vec_cbow_300d_features](old/fastText_oS+Sm_vec_cbow_300d_features) | baseline_features + fastText + oscarS_Supermat + cbow + 300d | 74.43  |  74.38  |  74.40  |
| [fastText_oS+Sm_vec_skipgram_100d_no_features](old/fastText_oS+Sm_vec_skipgram_100d_no_features) | baseline_no_features + fastText + oscarS_Supermat + skipgram + 100d | 74.22  |  75.99  |  75.08 | 
| [fastText_oS+Sm_vec_skipgram_100d_features](old/fastText_oS+Sm_vec_skipgram_100d_features) | baseline_features + fastText + oscarS_Supermat + skipgram + 100d | 75.72  |  75.89  |  75.78 |