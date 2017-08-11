import React, { Component } from 'react';
import { connect } from 'react-redux';
import { bindActionCreators } from 'redux';
import DataPreviewer from '../UI/DataPreviewer';

class DistributionPreview extends Component {
  render(){
    return (<div className='dataset-preview container'>
                  {<DataPreviewer distribution={this.props.distribution}/>}
            </div>)
  }
}

function mapStateToProps(state) {
  const distribution =state.record.distribution;
  return {
    distribution
  };
}


export default connect(mapStateToProps)(DistributionPreview);
